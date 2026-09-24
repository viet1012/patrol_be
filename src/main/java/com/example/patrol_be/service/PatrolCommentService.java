
package com.example.patrol_be.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.patrol_be.repository.PatrolCommentRepo;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Service
public class PatrolCommentService {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
	private static final int MAX_OUTPUT_TOKENS = 300;
	private static final int MAX_RETRY = 1;
	private static final long SEMAPHORE_WAIT_SECONDS = 3;

	private final Semaphore llmSemaphore = new Semaphore(1, true);

	private final PatrolCommentRepo repo;
	private final ObjectMapper mapper;
	private final HttpClient httpClient;

	private final String lmUrl;
	private final String lmApiKey;
	private final String lmModel;

	public PatrolCommentService(
			PatrolCommentRepo repo,
			ObjectMapper mapper,
			@Value("${lm.url:http://192.168.122.16:1234}") String lmUrl,
			@Value("${lm.apiKey:}") String lmApiKey,
			@Value("${lm.model:openai/gpt-oss-20b}") String lmModel
	) {
		this.repo = repo;
		this.mapper = mapper;
		this.lmUrl = removeTrailingSlash(lmUrl);
		this.lmApiKey = normalize(lmApiKey);
		this.lmModel = normalize(lmModel) == null
				? "openai/gpt-oss-20b"
				: lmModel.trim();

		this.httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(CONNECT_TIMEOUT)
				.build();
	}

	public String getTranslateDefault(String inputText) {
		final String original = normalize(inputText);

		if (original == null) {
			return inputText;
		}

		if (!shouldTranslate(original)) {
			log.info("[TRANSLATE] Skip non-translatable input: [{}]", abbreviate(original, 160));
			return original;
		}

		final boolean sourceIsJapanese = containsJapanese(original);

		try {
			Optional<String> found = repo.findTranslatedText(original);

			if (found.isPresent()) {
				String cached = normalize(found.get());

				log.info(
						"[TRANSLATE] Cache found: sourceJapanese={}, original=[{}], cached=[{}]",
						sourceIsJapanese,
						abbreviate(original, 160),
						abbreviate(cached, 160)
				);

				if (isValidTranslation(original, cached, sourceIsJapanese)) {
					return cached;
				}

				log.warn(
						"[TRANSLATE] Ignore invalid cache: original=[{}], cached=[{}]",
						abbreviate(original, 160),
						abbreviate(cached, 160)
				);
			}

			log.info(
					"[TRANSLATE] Calling LLM: sourceJapanese={}, text=[{}]",
					sourceIsJapanese,
					abbreviate(original, 160)
			);

			String translated = normalize(
					translateWithRetry(original, sourceIsJapanese)
			);

			log.info(
					"[TRANSLATE] LLM result: original=[{}], translated=[{}]",
					abbreviate(original, 160),
					abbreviate(translated, 160)
			);

			if (!isValidTranslation(original, translated, sourceIsJapanese)) {
				log.warn(
						"[TRANSLATE] Invalid LLM translation. Return original. original=[{}], translated=[{}]",
						abbreviate(original, 160),
						abbreviate(translated, 160)
				);
				return original;
			}

			return translated;

		} catch (Exception exception) {
			log.error(
					"[TRANSLATE] Unexpected error. Return original. text=[{}]",
					abbreviate(original, 160),
					exception
			);
			return original;
		}
	}

	private String translateWithRetry(String text, boolean sourceIsJapanese) {
		boolean acquired = false;

		try {
			long waitStartedAt = System.nanoTime();
			acquired = llmSemaphore.tryAcquire(SEMAPHORE_WAIT_SECONDS, TimeUnit.SECONDS);

			if (!acquired) {
				log.warn(
						"[TRANSLATE] LLM capacity unavailable. Skip translation after waiting {} ms.",
						TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStartedAt)
				);
				return null;
			}

			int attempt = 0;

			while (true) {
				try {
					return translateLLM(text, sourceIsJapanese);
				} catch (HttpTimeoutException timeoutException) {
					if (attempt >= MAX_RETRY) {
						throw timeoutException;
					}

					attempt++;

					log.warn(
							"[TRANSLATE] Timeout. Retry {}/{}. text=[{}]",
							attempt,
							MAX_RETRY,
							abbreviate(text, 160)
					);

					sleepQuietly(700);
				}
			}

		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			log.warn("[TRANSLATE] Thread interrupted while waiting for LLM.", exception);
			return null;

		} catch (Exception exception) {
			log.error(
					"[TRANSLATE] LLM call failed. text=[{}], error={}",
					abbreviate(text, 160),
					exception.toString(),
					exception
			);
			return null;

		} finally {
			if (acquired) {
				llmSemaphore.release();
			}
		}
	}

	private String translateLLM(
			String text,
			boolean sourceIsJapanese
	) throws IOException, InterruptedException {
		String normalizedText = normalize(text);

		if (normalizedText == null) {
			return text;
		}

		ObjectNode payload = buildPayload(normalizedText, sourceIsJapanese);

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(lmUrl + "/v1/chat/completions"))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(
						HttpRequest.BodyPublishers.ofString(
								payload.toString(),
								StandardCharsets.UTF_8
						)
				);

		if (lmApiKey != null) {
			requestBuilder.header("Authorization", "Bearer " + lmApiKey);
		}

		long startedAt = System.nanoTime();

		HttpResponse<String> response = httpClient.send(
				requestBuilder.build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
		);

		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
				System.nanoTime() - startedAt
		);

		int status = response.statusCode();

		if (status == 200) {
			String result = extractContent(response.body());

			log.info(
					"[TRANSLATE] Completed in {} ms. inputLength={}, outputLength={}",
					elapsedMs,
					normalizedText.length(),
					result == null ? 0 : result.length()
			);

			return result;
		}

		if (status == 429) {
			log.warn("[TRANSLATE] LLM rate limited. status=429, elapsedMs={}", elapsedMs);
			return null;
		}

		log.warn(
				"[TRANSLATE] LLM returned error. status={}, elapsedMs={}, body={}",
				status,
				elapsedMs,
				abbreviate(response.body(), 600)
		);

		return null;
	}

	private ObjectNode buildPayload(String text, boolean sourceIsJapanese) {
		ObjectNode payload = mapper.createObjectNode();

		payload.put("model", lmModel);
		payload.put("temperature", 0.1);
		payload.put("max_tokens", MAX_OUTPUT_TOKENS);
		payload.put("stream", false);

		ArrayNode messages = payload.putArray("messages");

		ObjectNode systemMessage = mapper.createObjectNode();
		systemMessage.put("role", "system");
		systemMessage.put(
				"content",
				sourceIsJapanese
						? japaneseToVietnamesePrompt()
						: vietnameseToJapanesePrompt()
		);
		messages.add(systemMessage);

		ObjectNode userMessage = mapper.createObjectNode();
		userMessage.put("role", "user");
		userMessage.put("content", text);
		messages.add(userMessage);

		return payload;
	}

	private String vietnameseToJapanesePrompt() {
		return """
                You are a professional Vietnamese-to-Japanese translator
                for factory safety patrols, 5S audits, and manufacturing reports.

                Mandatory rules:

                1. Translate the input into natural, professional Japanese
                   used in Japanese manufacturing factories.

                2. The input can be Vietnamese, English, or mixed Vietnamese-English.

                3. Vietnamese without proper diacritics must be interpreted
                   and corrected according to the safety context before translation.

                4. Correct obvious Vietnamese spelling mistakes based on context.
                   Example: "đỗ ngã" in a safety report means "đổ ngã".

                5. Preserve factory abbreviations and identifiers such as:
                   MTC, PLC, HSE, QA, QR, 5S, machine codes, area codes,
                   equipment codes, model names, and part numbers.

                6. Preserve technical meanings related to safety, machinery,
                   production, tools, work areas, risk levels, falling,
                   overturning, slipping, collision, electric shock,
                   fire, leakage, and mechanical hazards.

                7. Never return the original Vietnamese or English sentence unchanged.

                8. Return only the final Japanese translation.

                9. Do not return JSON, Markdown, explanations, labels,
                   language names, quotes, or the original text.
                """;
	}

	private String japaneseToVietnamesePrompt() {
		return """
                You are a professional Japanese-to-Vietnamese translator
                for factory safety patrols, 5S audits, and manufacturing reports.

                Mandatory rules:

                1. Translate the Japanese input into natural Vietnamese
                   with correct diacritics.

                2. Preserve factory abbreviations and identifiers such as:
                   MTC, PLC, HSE, QA, QR, 5S, machine codes, area codes,
                   equipment codes, model names, and part numbers.

                3. Preserve the technical meaning used in safety,
                   machinery, manufacturing, production, and 5S reports.

                4. Never return the original Japanese sentence unchanged.

                5. Return only the final Vietnamese translation.

                6. Do not return JSON, Markdown, explanations, labels,
                   language names, quotes, or the original text.
                """;
	}

	private String extractContent(String responseBody) throws IOException {
		if (responseBody == null || responseBody.isBlank()) {
			return null;
		}

		JsonNode root = mapper.readTree(responseBody);

		JsonNode contentNode = root.path("choices")
				.path(0)
				.path("message")
				.path("content");

		if (contentNode.isMissingNode() || contentNode.isNull()) {
			log.warn(
					"[TRANSLATE] Response missing choices[0].message.content. body={}",
					abbreviate(responseBody, 600)
			);
			return null;
		}

		String content = normalize(contentNode.asText());

		if (content == null) {
			return null;
		}

		return cleanModelOutput(content);
	}

	private String cleanModelOutput(String value) {
		String result = value
				.replace("```text", "")
				.replace("```json", "")
				.replace("```", "")
				.trim();

		result = result
				.replaceFirst(
						"^(?i)(translation|translated text|japanese|vietnamese|bản dịch|dịch)\\s*:\\s*",
						""
				)
				.trim();

		if (result.length() >= 2) {
			boolean doubleQuoted = result.startsWith("\"") && result.endsWith("\"");
			boolean singleQuoted = result.startsWith("'") && result.endsWith("'");

			if (doubleQuoted || singleQuoted) {
				result = result.substring(1, result.length() - 1).trim();
			}
		}

		return normalize(result);
	}

	private boolean shouldTranslate(String text) {
		String value = normalize(text);

		if (value == null) {
			return false;
		}

		return value.codePoints().anyMatch(Character::isLetter);
	}

	private boolean isValidTranslation(
			String original,
			String translated,
			boolean sourceIsJapanese
	) {
		String source = normalize(original);
		String target = normalize(translated);

		if (source == null || target == null) {
			return false;
		}

		if (source.equalsIgnoreCase(target)) {
			return false;
		}

		if (sourceIsJapanese) {
			return containsLatinLetter(target) && !isMostlyJapanese(target);
		}

		return containsJapanese(target);
	}

	private static boolean containsJapanese(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}

		return value.codePoints().anyMatch(
				codePoint -> isHiragana(codePoint)
						|| isKatakana(codePoint)
						|| isCjk(codePoint)
		);
	}

	private static boolean isMostlyJapanese(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}

		long letters = value.codePoints()
				.filter(Character::isLetter)
				.count();

		if (letters == 0) {
			return false;
		}

		long japaneseLetters = value.codePoints()
				.filter(
						codePoint -> isHiragana(codePoint)
								|| isKatakana(codePoint)
								|| isCjk(codePoint)
				)
				.count();

		return japaneseLetters * 100 / letters >= 60;
	}

	private static boolean containsLatinLetter(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}

		return value.codePoints().anyMatch(
				codePoint -> Character.isLetter(codePoint)
						&& !isHiragana(codePoint)
						&& !isKatakana(codePoint)
						&& !isCjk(codePoint)
		);
	}

	private static boolean isHiragana(int codePoint) {
		return codePoint >= 0x3040 && codePoint <= 0x309F;
	}

	private static boolean isKatakana(int codePoint) {
		return codePoint >= 0x30A0 && codePoint <= 0x30FF;
	}

	private static boolean isCjk(int codePoint) {
		return codePoint >= 0x4E00 && codePoint <= 0x9FFF;
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}

		String normalized = value
				.replace('\u00A0', ' ')
				.trim();

		return normalized.isEmpty() ? null : normalized;
	}

	private static String removeTrailingSlash(String value) {
		String result = normalize(value);

		if (result == null) {
			return "http://192.168.122.16:1234";
		}

		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}

		return result;
	}

	private static String abbreviate(String value, int maxLength) {
		if (value == null) {
			return null;
		}

		if (value.length() <= maxLength) {
			return value;
		}

		return value.substring(0, maxLength) + "...";
	}

	private static void sleepQuietly(long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}