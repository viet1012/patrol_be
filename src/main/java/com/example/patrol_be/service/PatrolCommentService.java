//package com.example.patrol_be.service;
//
//import com.example.patrol_be.repository.PatrolCommentRepo;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ArrayNode;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.io.IOException;
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.time.Duration;
//import java.util.Optional;
//
//@Service
//public class PatrolCommentService {
//	private static final Logger log = LoggerFactory.getLogger(PatrolCommentService.class);
//
//	private final PatrolCommentRepo repo;
//	private final ObjectMapper mapper = new ObjectMapper();
//	private final HttpClient httpClient;
//	private final String lmUrl;
//	private final String lmApiKey;
//
//	public PatrolCommentService(PatrolCommentRepo repo,
//	                            @Value("${lm.url:http://192.168.122.16:1234}") String lmUrl,
//	                            @Value("${lm.apiKey:}") String lmApiKey) {
//		this.repo = repo;
//		this.lmUrl = lmUrl;
//		this.lmApiKey = lmApiKey;
//		this.httpClient = HttpClient.newBuilder()
//				.version(HttpClient.Version.HTTP_1_1)
//				.connectTimeout(Duration.ofSeconds(5))
//				.build();
//	}
//
//	/**
//	 * 1) Tìm trong DB bản dịch mặc định
//	 * 2) Nếu không có -> gọi LLM (an toàn: catch exception)
//	 * 3) Nếu LLM trả về -> trả kết quả (và tùy chọn: persist vào DB)
//	 * 4) Nếu LLM fail -> trả input gốc
//	 */
//	public String getTranslateDefault(String inputText) {
//		if (inputText == null || inputText.isBlank()) return inputText;
//
//		try {
//			Optional<String> found = repo.findTranslatedText(inputText);
//			if (found.isPresent()) {
//				return found.get();
//			}
//
//			String translated = translateLLMSafe(inputText);
//			if (translated != null && !translated.isBlank()) {
//				// TODO: nếu muốn lưu kết quả mới vào DB, gọi repo.save(...) hoặc repo.saveTranslated(...)
//				return translated;
//			} else {
//				return inputText;
//			}
//		} catch (Exception ex) {
//			log.error("Error while getting translation, return original text", ex);
//			return inputText;
//		}
//	}
//
//	// wrapper an toàn: sẽ bắt tất cả exception và trả null nếu fail
//	private String translateLLMSafe(String text) {
//		try {
//			return translateLLM(text);
//		} catch (Exception e) {
//			log.warn("LLM translate failed for text='{}': {}", text, e.toString());
//			return null;
//		}
//	}
//
//	// thực thi HTTP call tới LLM; ném IOException / InterruptedException nếu cần (được wrapper bởi translateLLMSafe)
//	private String translateLLM(String text) throws IOException, InterruptedException {
//		if (text == null || text.isBlank()) return text;
//
//		String systemPrompt =
//				"You are a professional translator specializing in factory safety patrols, 5S audits, and mechanical manufacturing environments.\n" +
//						"The input text is a comment from a safety/5S inspection report and can be in one of these languages:\n" +
//						"• Vietnamese (with or without diacritics)\n" +
//						"• Japanese\n" +
//						"• English\n" +
//						"• Or a mix of the above\n\n" +
//
//						"Follow these rules exactly:\n" +
//						"1. First, detect the primary language of the input.\n" +
//						"2. If the text is Vietnamese WITHOUT proper diacritics (e.g. 'kiem tra may moc'), restore it to correct Vietnamese with full diacritics first.\n" +
//						"3. Translation rules:\n" +
//						"   • If the primary language is Japanese → translate to natural, accurate Vietnamese (with correct diacritics).\n" +
//						"   • If the primary language is ANYTHING ELSE (Vietnamese with/without diacritics, English, mixed, etc.) → translate to natural, professional Japanese used in Japanese manufacturing factories.\n" +
//						"4. Preserve all technical terms related to safety, 5S (整理・整頓・清掃・清潔・躾), machinery, risk levels, production areas, tools, etc.\n" +
//						"5. Return ONLY the final translated text. Do not include explanations, labels, quotes, original text, or language names.\n" +
//						"6. Use newlines (\\n) to maintain the original formatting when needed.";
//
//		ObjectNode payload = mapper.createObjectNode();
//		payload.put("model", "openai/gpt-oss-20b");
//		payload.put("temperature", 0.2);
//		payload.put("max_tokens", 2000);
//
//		ArrayNode messages = payload.putArray("messages");
//		ObjectNode sys = mapper.createObjectNode();
//		sys.put("role", "system");
//		sys.put("content", systemPrompt);
//		messages.add(sys);
//
//		ObjectNode usr = mapper.createObjectNode();
//		usr.put("role", "user");
//		usr.put("content", text);
//		messages.add(usr);
//
//		String endpoint = lmUrl.endsWith("/") ? lmUrl + "v1/chat/completions" : lmUrl + "/v1/chat/completions";
//
//		HttpRequest.Builder builder = HttpRequest.newBuilder()
//				.uri(URI.create(endpoint))
//				.timeout(Duration.ofSeconds(10)) // rất quan trọng: tránh treo
//				.header("Content-Type", "application/json")
//				.POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
//
//		if (lmApiKey != null && !lmApiKey.isBlank()) {
//			builder.header("Authorization", "Bearer " + lmApiKey);
//		}
//
//		HttpRequest req = builder.build();
//		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
//
//		int status = resp.statusCode();
//		if (status == 200) {
//
//			JsonNode root = mapper.readTree(resp.body());
//
//			JsonNode contentNode = root.path("choices")
//					.path(0)
//					.path("message")
//					.path("content");
//
//			if (contentNode.isMissingNode()) {
//				log.warn("LLM response missing content field. body={}", resp.body());
//				return "{}";
//			}
//
//			String content = contentNode.asText();
//
//			log.info("LLM RAW CONTENT:\n{}", content);
//
//			// bỏ markdown fence
//			content = content
//					.replace("```json", "")
//					.replace("```", "")
//					.trim();
//
//			// tìm JSON đầu tiên
//			int start = content.indexOf('{');
//			int end = content.lastIndexOf('}');
//
//			if (start >= 0 && end > start) {
//				content = content.substring(start, end + 1);
//			}
//
//			log.info("LLM CLEAN JSON:\n{}", content);
//
//			return content;
//		} else if (status == 429) {
//			log.warn("LLM rate-limited (429)");
//			return null;
//		} else {
//			log.warn("LLM returned non-200 status: {} body={}", status, resp.body());
//			return null;
//		}
//	}
//
//
//}
package com.example.patrol_be.service;

import com.example.patrol_be.repository.PatrolCommentRepo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

@Slf4j
@Service
public class PatrolCommentService {

	private static final Duration CONNECT_TIMEOUT =
			Duration.ofSeconds(10);

	private static final Duration REQUEST_TIMEOUT =
			Duration.ofSeconds(90);

	private static final int MAX_OUTPUT_TOKENS = 300;

	/*
	 * Chỉ cho một request dùng model tại một thời điểm.
	 * Phù hợp khi LM Studio đang để Parallel = 1.
	 */
	private final Semaphore llmSemaphore =
			new Semaphore(1, true);

	private final PatrolCommentRepo repo;
	private final ObjectMapper mapper;
	private final HttpClient httpClient;

	private final String lmUrl;
	private final String lmApiKey;
	private final String lmModel;

	public PatrolCommentService(
			PatrolCommentRepo repo,
			ObjectMapper mapper,
			@Value("${lm.url:http://192.168.122.16:1234}")
			String lmUrl,
			@Value("${lm.apiKey:}")
			String lmApiKey,
			@Value("${lm.model:openai/gpt-oss-20b}")
			String lmModel
	) {
		this.repo = repo;
		this.mapper = mapper;
		this.lmUrl = removeTrailingSlash(lmUrl);
		this.lmApiKey = lmApiKey;
		this.lmModel = lmModel;

		this.httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(CONNECT_TIMEOUT)
				.build();
	}

	// ============================================================
	// PUBLIC API
	// ============================================================

	public String getTranslateDefault(String inputText) {
		String original = normalize(inputText);

		if (original == null) {
			return inputText;
		}

		/*
		 * Không gọi LLM cho nội dung quá ngắn.
		 * Tránh chờ model chỉ để dịch "test", "ok", "-", "NG"...
		 */
		if (!shouldTranslate(original)) {
			return original;
		}

		try {
			Optional<String> found =
					repo.findTranslatedText(original);

			if (found.isPresent()) {
				String cached = normalize(found.get());

				if (cached != null) {
					return cached;
				}
			}

			String translated =
					translateWithRetry(original);

			return normalize(translated) == null
					? original
					: translated.trim();

		} catch (Exception exception) {
			log.error(
					"Cannot get translation. Return original text. text={}",
					abbreviate(original, 120),
					exception
			);

			return original;
		}
	}

	// ============================================================
	// RETRY + CONCURRENCY CONTROL
	// ============================================================

	private String translateWithRetry(String text) {
		boolean acquired = false;

		try {
			acquired = llmSemaphore.tryAcquire(
					3,
					TimeUnit.SECONDS
			);

			if (!acquired) {
				log.warn(
						"LLM is busy. Skip translation. text={}",
						abbreviate(text, 120)
				);

				return null;
			}

			try {
				return translateLLM(text);

			} catch (HttpTimeoutException timeoutException) {
				log.warn(
						"LLM request timed out. Retry once. text={}",
						abbreviate(text, 120)
				);

				sleepQuietly(700);

				return translateLLM(text);
			}

		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();

			log.warn(
					"Thread interrupted while waiting for LLM.",
					exception
			);

			return null;

		} catch (Exception exception) {
			log.warn(
					"LLM translation failed. text={}, error={}",
					abbreviate(text, 120),
					exception.toString()
			);

			return null;

		} finally {
			if (acquired) {
				llmSemaphore.release();
			}
		}
	}

	// ============================================================
	// LLM HTTP CALL
	// ============================================================

	private String translateLLM(
			String text
	) throws IOException, InterruptedException {

		String normalizedText = normalize(text);

		if (normalizedText == null) {
			return text;
		}

		ObjectNode payload = buildPayload(
				normalizedText
		);

		HttpRequest.Builder requestBuilder =
				HttpRequest.newBuilder()
						.uri(URI.create(
								lmUrl + "/v1/chat/completions"
						))
						.timeout(REQUEST_TIMEOUT)
						.header(
								"Content-Type",
								"application/json"
						)
						.header(
								"Accept",
								"application/json"
						)
						.POST(
								HttpRequest.BodyPublishers.ofString(
										payload.toString(),
										StandardCharsets.UTF_8
								)
						);

		if (lmApiKey != null
				&& !lmApiKey.isBlank()) {
			requestBuilder.header(
					"Authorization",
					"Bearer " + lmApiKey.trim()
			);
		}

		long startedAt = System.nanoTime();

		HttpResponse<String> response =
				httpClient.send(
						requestBuilder.build(),
						HttpResponse.BodyHandlers.ofString(
								StandardCharsets.UTF_8
						)
				);

		long elapsedMs =
				TimeUnit.NANOSECONDS.toMillis(
						System.nanoTime() - startedAt
				);

		if (response.statusCode() == 200) {
			String result = extractContent(
					response.body()
			);

			log.info(
					"LLM translation completed in {} ms. inputLength={}, outputLength={}",
					elapsedMs,
					normalizedText.length(),
					result == null ? 0 : result.length()
			);

			return result;
		}

		if (response.statusCode() == 429) {
			log.warn(
					"LLM rate limited. status=429, elapsedMs={}",
					elapsedMs
			);

			return null;
		}

		log.warn(
				"LLM returned error. status={}, elapsedMs={}, body={}",
				response.statusCode(),
				elapsedMs,
				abbreviate(response.body(), 500)
		);

		return null;
	}

	// ============================================================
	// REQUEST BODY
	// ============================================================

	private ObjectNode buildPayload(String text) {
		ObjectNode payload =
				mapper.createObjectNode();

		payload.put("model", lmModel);
		payload.put("temperature", 0.1);
		payload.put("max_tokens", MAX_OUTPUT_TOKENS);
		payload.put("stream", false);

		ArrayNode messages =
				payload.putArray("messages");

		ObjectNode systemMessage =
				mapper.createObjectNode();

		systemMessage.put("role", "system");
		systemMessage.put(
				"content",
				"""
				You are a professional translator for factory safety patrols, \
				5S audits, and mechanical manufacturing environments.

				Detect the primary language of the input.

				Translation rules:
				- Japanese input: translate into natural Vietnamese with correct diacritics.
				- Vietnamese, English, or mixed input: translate into natural professional Japanese used in manufacturing factories.
				- Vietnamese without diacritics: restore the correct Vietnamese meaning before translating.
				- Preserve safety, 5S, machine, production, risk-level, tool, and technical terms.
				- Preserve line breaks when useful.
				- Return only the translated text.
				- Do not return JSON.
				- Do not include explanations, labels, quotes, or the original text.
				"""
		);

		messages.add(systemMessage);

		ObjectNode userMessage =
				mapper.createObjectNode();

		userMessage.put("role", "user");
		userMessage.put("content", text);

		messages.add(userMessage);

		return payload;
	}

	// ============================================================
	// RESPONSE PARSING
	// ============================================================

	private String extractContent(
			String responseBody
	) throws IOException {

		if (responseBody == null
				|| responseBody.isBlank()) {
			return null;
		}

		JsonNode root =
				mapper.readTree(responseBody);

		JsonNode contentNode =
				root.path("choices")
						.path(0)
						.path("message")
						.path("content");

		if (contentNode.isMissingNode()
				|| contentNode.isNull()) {
			log.warn(
					"LLM response missing choices[0].message.content. body={}",
					abbreviate(responseBody, 500)
			);

			return null;
		}

		String content =
				normalize(contentNode.asText());

		if (content == null) {
			return null;
		}

		/*
		 * Chỉ bỏ markdown fence nếu model tự thêm.
		 * Không tìm dấu { } vì kết quả là plain text, không phải JSON.
		 */
		return content
				.replace("```text", "")
				.replace("```json", "")
				.replace("```", "")
				.trim();
	}

	// ============================================================
	// FILTER
	// ============================================================

	private boolean shouldTranslate(String text) {
		if (text == null) {
			return false;
		}

		String value = text.trim();

		if (value.length() < 5) {
			return false;
		}

		if (value.matches("^[\\d\\W_]+$")) {
			return false;
		}

		return true;
	}

	// ============================================================
	// HELPERS
	// ============================================================

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}

		String normalized = value
				.replace('\u00A0', ' ')
				.trim();

		return normalized.isEmpty()
				? null
				: normalized;
	}

	private static String removeTrailingSlash(
			String value
	) {
		if (value == null || value.isBlank()) {
			return "http://192.168.122.16:1234";
		}

		String result = value.trim();

		while (result.endsWith("/")) {
			result = result.substring(
					0,
					result.length() - 1
			);
		}

		return result;
	}

	private static String abbreviate(
			String value,
			int maxLength
	) {
		if (value == null) {
			return null;
		}

		if (value.length() <= maxLength) {
			return value;
		}

		return value.substring(
				0,
				maxLength
		) + "...";
	}

	private static void sleepQuietly(
			long milliseconds
	) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}