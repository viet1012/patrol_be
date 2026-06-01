package com.example.patrol_be.service;

import com.example.patrol_be.repository.PatrolCommentRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Service
public class PatrolCommentService {
	private static final Logger log = LoggerFactory.getLogger(PatrolCommentService.class);

	private final PatrolCommentRepo repo;
	private final ObjectMapper mapper = new ObjectMapper();
	private final HttpClient httpClient;
	private final String lmUrl;
	private final String lmApiKey;

	public PatrolCommentService(PatrolCommentRepo repo,
	                            @Value("${lm.url:http://192.168.122.16:1234}") String lmUrl,
	                            @Value("${lm.apiKey:}") String lmApiKey) {
		this.repo = repo;
		this.lmUrl = lmUrl;
		this.lmApiKey = lmApiKey;
		this.httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5))
				.build();
	}

	/**
	 * 1) Tìm trong DB bản dịch mặc định
	 * 2) Nếu không có -> gọi LLM (an toàn: catch exception)
	 * 3) Nếu LLM trả về -> trả kết quả (và tùy chọn: persist vào DB)
	 * 4) Nếu LLM fail -> trả input gốc
	 */
	public String getTranslateDefault(String inputText) {
		if (inputText == null || inputText.isBlank()) return inputText;

		try {
			Optional<String> found = repo.findTranslatedText(inputText);
			if (found.isPresent()) {
				return found.get();
			}

			String translated = translateLLMSafe(inputText);
			if (translated != null && !translated.isBlank()) {
				// TODO: nếu muốn lưu kết quả mới vào DB, gọi repo.save(...) hoặc repo.saveTranslated(...)
				return translated;
			} else {
				return inputText;
			}
		} catch (Exception ex) {
			log.error("Error while getting translation, return original text", ex);
			return inputText;
		}
	}

	// wrapper an toàn: sẽ bắt tất cả exception và trả null nếu fail
	private String translateLLMSafe(String text) {
		try {
			return translateLLM(text);
		} catch (Exception e) {
			log.warn("LLM translate failed for text='{}': {}", text, e.toString());
			return null;
		}
	}

	// thực thi HTTP call tới LLM; ném IOException / InterruptedException nếu cần (được wrapper bởi translateLLMSafe)
	private String translateLLM(String text) throws IOException, InterruptedException {
		if (text == null || text.isBlank()) return text;

		String systemPrompt =
				"You are a professional translator specializing in factory safety patrols, 5S audits, and mechanical manufacturing environments.\n" +
						"The input text is a comment from a safety/5S inspection report and can be in one of these languages:\n" +
						"• Vietnamese (with or without diacritics)\n" +
						"• Japanese\n" +
						"• English\n" +
						"• Or a mix of the above\n\n" +

						"Follow these rules exactly:\n" +
						"1. First, detect the primary language of the input.\n" +
						"2. If the text is Vietnamese WITHOUT proper diacritics (e.g. 'kiem tra may moc'), restore it to correct Vietnamese with full diacritics first.\n" +
						"3. Translation rules:\n" +
						"   • If the primary language is Japanese → translate to natural, accurate Vietnamese (with correct diacritics).\n" +
						"   • If the primary language is ANYTHING ELSE (Vietnamese with/without diacritics, English, mixed, etc.) → translate to natural, professional Japanese used in Japanese manufacturing factories.\n" +
						"4. Preserve all technical terms related to safety, 5S (整理・整頓・清掃・清潔・躾), machinery, risk levels, production areas, tools, etc.\n" +
						"5. Return ONLY the final translated text. Do not include explanations, labels, quotes, original text, or language names.\n" +
						"6. Use newlines (\\n) to maintain the original formatting when needed.";

		ObjectNode payload = mapper.createObjectNode();
		payload.put("model", "openai/gpt-oss-20b");
		payload.put("temperature", 0.2);
		payload.put("max_tokens", 2000);

		ArrayNode messages = payload.putArray("messages");
		ObjectNode sys = mapper.createObjectNode();
		sys.put("role", "system");
		sys.put("content", systemPrompt);
		messages.add(sys);

		ObjectNode usr = mapper.createObjectNode();
		usr.put("role", "user");
		usr.put("content", text);
		messages.add(usr);

		String endpoint = lmUrl.endsWith("/") ? lmUrl + "v1/chat/completions" : lmUrl + "/v1/chat/completions";

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.timeout(Duration.ofSeconds(10)) // rất quan trọng: tránh treo
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

		if (lmApiKey != null && !lmApiKey.isBlank()) {
			builder.header("Authorization", "Bearer " + lmApiKey);
		}

		HttpRequest req = builder.build();
		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

		int status = resp.statusCode();
		if (status == 200) {

			JsonNode root = mapper.readTree(resp.body());

			JsonNode contentNode = root.path("choices")
					.path(0)
					.path("message")
					.path("content");

			if (contentNode.isMissingNode()) {
				log.warn("LLM response missing content field. body={}", resp.body());
				return "{}";
			}

			String content = contentNode.asText();

			log.info("LLM RAW CONTENT:\n{}", content);

			// bỏ markdown fence
			content = content
					.replace("```json", "")
					.replace("```", "")
					.trim();

			// tìm JSON đầu tiên
			int start = content.indexOf('{');
			int end = content.lastIndexOf('}');

			if (start >= 0 && end > start) {
				content = content.substring(start, end + 1);
			}

			log.info("LLM CLEAN JSON:\n{}", content);

			return content;
		} else if (status == 429) {
			log.warn("LLM rate-limited (429)");
			return null;
		} else {
			log.warn("LLM returned non-200 status: {} body={}", status, resp.body());
			return null;
		}
	}

	public String analyzeMachineIssues(String machineJson) {
		if (machineJson == null || machineJson.isBlank()) {
			return """
					{
					  "found": false,
					  "message": "Input machine history is empty",
					  "commonIssues": []
					}
					""";
		}

		try {
			return analyzeMachineIssuesLLM(machineJson);
		} catch (Exception e) {
			e.printStackTrace();

			return """
					{
					  "found": false,
					  "message": "LLM call failed: %s",
					  "commonIssues": []
					}
					""".formatted(e.getMessage());
		}
	}

	private String analyzeMachineIssuesLLM(String machineJson)
			throws IOException, InterruptedException {
		String systemPrompt = """
							You are an HSE manufacturing safety analyst.
							
							Analyze machine patrol issue history from JSON input.
							
							Return ONLY valid JSON:
							
							{
							  "summaryVi": "",
							  "summaryJp": ""
							}
							
							Language Rules:
							
							- summaryVi must be 100% Vietnamese.
							- summaryJp must be 100% Japanese.
							- Do not mix languages.
							
							Identifier Rules:
							
							- Keep fac unchanged.
							- Keep division unchanged.
							- Keep area unchanged.
							- Keep machine unchanged.
							- Never translate identifiers.
							
							Analysis Rules:
							
							- Read all comments.
							- Identify recurring issues.
							- Identify high-risk issues.
							- Merge only truly similar issues.
							- Do not invent information.
							- Do not ignore distinct issues.
							
							summaryVi format:
							
							Tổng quan:
							<Một câu ngắn>
							
							Vấn đề chính:
							- <3~8 từ>
							- <3~8 từ>
							- <3~8 từ>
							
							Khuyến nghị:
							- <3~8 từ>
							- <3~8 từ>
							
							summaryJp format:
							
							概要：
							<短い説明>
							
							主な問題：
							- <10文字以内>
							- <10文字以内>
							- <10文字以内>
							
							推奨対策：
							- <10文字以内>
							- <10文字以内>
							
							Output Rules:
							
							- Return JSON only.
							- No markdown.
							- No explanation.
							- summaryVi maximum 80 words.
							- summaryJp maximum 120 characters.
							- Keep every bullet short and concise.
							""";
//		String systemPrompt = """
//				You are an HSE manufacturing safety analyst.
//
//				Return ONLY JSON:
//
//				{
//				  "summaryVi":"",
//				  "summaryJp":""
//				}
//
//				Requirements:
//
//				summaryVi format:
//				"[machine] tại [fac] ghi nhận các vấn đề chính gồm ... . Các rủi ro tập trung tại ... . Cần ưu tiên khắc phục ..."
//
//				summaryJp format:
//				"[machine]では ... が確認された。優先対策が必要である。"
//
//				Rules:
//				- Use identifiers exactly as provided.
//				- Do not translate fac, division, area, machine.
//				- Summarize all important issues.
//				- Merge only similar issues.
//				- No hallucination.
//				- No markdown.
//				- JSON only.
//				""";
//		String systemPrompt = """
//				You are an HSE manufacturing safety assistant.
//
//				Analyze machine patrol issue history from JSON input.
//
//				Return ONLY valid JSON.
//				No markdown.
//				No explanation.
//				No code block.
//
//				Requirements:
//
//				- Read all issue history records.
//				- Identify all recurring and important safety issues.
//				- Do not ignore distinct problems.
//				- Merge similar issues into a concise summary.
//				- Preserve machine context.
//				- Vietnamese must contain proper diacritics.
//				- Japanese must use natural factory/HSE terminology.
//				- If input contains broken Vietnamese encoding such as:
//				  d?y, nu?c, va cham...
//				  infer the intended Vietnamese meaning when possible.
//
//				Output JSON exactly:
//
//				{
//				  "summaryVi": "",
//				  "summaryJp": ""
//				}
//
//				summaryVi:
//				- 80~120 words
//				- Explain the most common issues found
//				- Mention recurring risks
//				- Mention affected areas if relevant
//				- Mention overall safety concerns
//
//				summaryJp:
//				- Natural Japanese
//				- Equivalent meaning to summaryVi
//				- Use factory/HSE wording
//
//				Return JSON only.
//				""";
		ObjectNode payload = mapper.createObjectNode();
		payload.put("model", "openai/gpt-oss-20b");
		payload.put("temperature", 0.0);
		payload.put("max_tokens", 600);

		ArrayNode messages = payload.putArray("messages");

		ObjectNode sys = mapper.createObjectNode();
		sys.put("role", "system");
		sys.put("content", systemPrompt);
		messages.add(sys);

		ObjectNode usr = mapper.createObjectNode();
		usr.put("role", "user");
		usr.put("content", machineJson);
		messages.add(usr);

		String endpoint = lmUrl.endsWith("/")
				? lmUrl + "v1/chat/completions"
				: lmUrl + "/v1/chat/completions";

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

		if (lmApiKey != null && !lmApiKey.isBlank()) {
			builder.header("Authorization", "Bearer " + lmApiKey);
		}

		HttpRequest req = builder.build();

		HttpResponse<String> resp =
				httpClient.send(req, HttpResponse.BodyHandlers.ofString());

		if (resp.statusCode() != 200) {
			log.warn("LLM analyze returned status={} body={}", resp.statusCode(), resp.body());
			return "{}";
		}

		JsonNode root = mapper.readTree(resp.body());

		String content = root.path("choices")
				.path(0)
				.path("message")
				.path("content")
				.asText("{}")
				.trim();

		String cleanJson = cleanJsonText(content);

		try {
			mapper.readTree(cleanJson);
			return cleanJson;
		} catch (Exception ex) {
			log.warn("Invalid JSON from LLM. raw={}", content);

			return """
					{
					  "found": false,
					  "message": "Invalid JSON from LLM",
					  "raw": %s,
					  "commonIssues": []
					}
					""".formatted(mapper.writeValueAsString(content));
		}
	}

	private String cleanJsonText(String text) {
		if (text == null || text.isBlank()) {
			return "{}";
		}

		String s = text.trim()
				.replace("```json", "")
				.replace("```", "")
				.trim();

		int start = s.indexOf('{');
		int end = s.lastIndexOf('}');

		if (start >= 0 && end > start) {
			return s.substring(start, end + 1).trim();
		}

		return "{}";
	}
}
