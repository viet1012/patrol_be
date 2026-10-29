package com.example.patrol_be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class PatrolMachineAiClientService {

	private static final Logger log =
			LoggerFactory.getLogger(PatrolMachineAiClientService.class);

	private static final String MODEL = "openai/gpt-oss-20b";

	private final ObjectMapper mapper;
	private final HttpClient httpClient;
	private final String lmUrl;
	private final String lmApiKey;

	public PatrolMachineAiClientService(
			@Value("${lm.url:http://192.168.122.16:1234}") String lmUrl,
			@Value("${lm.apiKey:}") String lmApiKey
	) {
		this.mapper = new ObjectMapper();
		this.lmUrl = normalizeBaseUrl(lmUrl);
		this.lmApiKey = lmApiKey;

		this.httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5))
				.build();
	}

	public String analyzeMachineIssues(String machineJson) {
		if (machineJson == null || machineJson.isBlank()) {
			return errorJson("Input machine history is empty");
		}

		try {
			ObjectNode payload = buildPayload(machineJson);
			String responseBody = callLlm(payload);
			return extractContentJson(responseBody);
		} catch (Exception e) {
			log.error("Analyze machine issues failed", e);
			return errorJson("LLM call failed: " + e.getMessage());
		}
	}

	private ObjectNode buildPayload(String machineJson) {
		ObjectNode payload = mapper.createObjectNode();

		payload.put("model", MODEL);
		payload.put("temperature", 0.1);
		payload.put("max_tokens", 700);
//		payload.put("top_p", 0.1);


		ArrayNode messages = payload.putArray("messages");

		messages.add(message("system", systemPrompt()));
		messages.add(message("user", machineJson));

		return payload;
	}

	private ObjectNode message(String role, String content) {
		ObjectNode node = mapper.createObjectNode();
		node.put("role", role);
		node.put("content", content);
		return node;
	}

	private String callLlm(ObjectNode payload) throws Exception {
		String endpoint = lmUrl + "/v1/chat/completions";

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.timeout(Duration.ofSeconds(120))
				.header("Content-Type", "application/json; charset=UTF-8")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(
						payload.toString(),
						StandardCharsets.UTF_8
				));

		if (lmApiKey != null && !lmApiKey.isBlank()) {
			builder.header("Authorization", "Bearer " + lmApiKey);
		}

		HttpResponse<String> response =
				httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		if (response.statusCode() != 200) {
			log.warn("LLM returned status={} body={}",
					response.statusCode(),
					response.body());

			throw new IllegalStateException(
					"LLM HTTP " + response.statusCode()
			);
		}

		return response.body();
	}

	private String extractContentJson(String responseBody) throws Exception {
		JsonNode root = mapper.readTree(responseBody);

		String content = root.path("choices")
				.path(0)
				.path("message")
				.path("content")
				.asText("")
				.trim();

		if (content.isBlank()) {
			log.warn("LLM content is empty. response={}", responseBody);
			return errorJson("LLM content is empty");
		}

		String cleanJson = cleanJsonText(content);

		if ("{}".equals(cleanJson)) {
			log.warn("Cannot extract JSON from LLM content. raw={}", content);
			return invalidJson(content);
		}

		try {
			mapper.readTree(cleanJson);
			return cleanJson;
		} catch (Exception e) {
			log.warn("Invalid JSON from LLM. raw={}", content);
			return invalidJson(content);
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

	private String errorJson(String message) {
		ObjectNode node = mapper.createObjectNode();
		node.put("found", false);
		node.put("message", message);
		node.put("summaryVi", "");
		node.put("summaryJp", "");
		node.putArray("commonIssues");
		return node.toString();
	}

	private String invalidJson(String raw) throws Exception {
		ObjectNode node = mapper.createObjectNode();
		node.put("found", false);
		node.put("message", "Invalid JSON from LLM");
		node.put("summaryVi", "");
		node.put("summaryJp", "");
		node.put("raw", raw);
		node.putArray("commonIssues");
		return mapper.writeValueAsString(node);
	}

	private String normalizeBaseUrl(String url) {
		if (url == null || url.isBlank()) {
			return "http://192.168.122.16:1234";
		}

		String s = url.trim();

		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}

		return s;
	}

	private String systemPrompt1() {
		return """
				You are a senior HSE (Health, Safety, and Environment) engineer specializing in manufacturing safety.
				
				Your task is to analyze patrol history collected from the selected machine or machines in the same category.
				
				Instructions:
				
				- Read every patrol comment carefully.
				- Group comments that describe the same safety issue.
				- Count how frequently each issue appears.
				- Focus only on the most common and most important issues.
				- Do not invent information.
				- Do not add unsupported numbers.
				- Ignore translation notes.
				- Preserve the original machine, facility, division, category and area information.
				- Base every conclusion only on the provided comments.
				
				Return ONLY one valid JSON object with exactly this schema:
				
				{
				  "summaryVi": ""
				}
				
				The value of "summaryVi" MUST be written entirely in Vietnamese.
				
				Required format:
				
				Tổng quan:
				<State only the top 1 or 2 most frequent issue groups and include an approximate percentage for each group.>
				
				Top lỗi thường gặp:
				
				1. <Lỗi> - <Khuyến nghị>
				2. <Lỗi> - <Khuyến nghị>
				3. <Lỗi> - <Khuyến nghị>
				4. <Lỗi> - <Khuyến nghị>
				5. <Lỗi> - <Khuyến nghị>
				
				Requirements for summaryVi:
				
				- Write ONLY Vietnamese.
				- Mention only the 1 or 2 dominant issue groups in the overview.
				- Include approximate percentages (estimated from the provided comments).
				- Do not write a generic introduction.
				- Sort issues by recurrence frequency (highest first).
				- Maximum 5 issues.
				- If fewer than 5 issues exist, return only those issues.
				- Each issue starts with an uppercase letter.
				- Each recommendation must be short, practical, and directly related to the issue.
				- Each line should be concise (preferably under 20 words).
				- Use professional HSE terminology.
				
				Output Rules:
				
				- Return JSON only.
				- Do not use Markdown.
				- Do not wrap the JSON inside code blocks.
				- Do not output explanations.
				- Do not output any additional fields.
				- Do not output any text before or after the JSON.
				""";
	}

	private String systemPrompt() {
		return """
				You are a senior HSE engineer for manufacturing safety.
				
				Analyze patrol history for the selected machine or same category machines.
				
				Rules:
				* Read all comments carefully.
				* Group recurring hazards by meaning.
				* Count recurrence frequency.
				* Focus on frequent and serious issues.
				* Do not invent facts.
				* Do not add unsupported numbers.
				* Keep machine, fac, division, area, cate unchanged.
				* Ignore translation notes.
				
				Return exactly ONE JSON object:
				
				{
				  "summaryVi": ""
				}
				
				summaryVi format:
				
				Tổng quan:
				<Mention only the top 1 or top 2 most frequent issue groups with approximate percentage share.>
				
				Examples:
				Che chắn an toàn chiếm khoảng 45%, chủ yếu do thiếu hoặc hỏng cover bảo vệ.
				Rủi ro điện chiếm khoảng 40%, tập trung ở ổ cắm và dây điện không an toàn.
				Thao tác gần bộ phận quay chiếm khoảng 35%, liên quan đến đá mài và trục quay.
				
				Top lỗi thường gặp:
				
				1. <Lỗi> → <Khuyến nghị>
				2. <Lỗi> → <Khuyến nghị>
				3. <Lỗi> → <Khuyến nghị>
				4. <Lỗi> → <Khuyến nghị>
				5. <Lỗi> → <Khuyến nghị>
				
				Requirements:
				* Vietnamese only.
				* Mention only top 1 or 2 dominant issue groups.
				* Include approximate percentage share.
				* Do not write generic overview.
				* Sort issues by recurrence frequency.
				* Maximum 5 issues.
				* Use numbered list.
				* No bullet symbols.
				* If fewer than 5 issues exist, return only available issues.
				* Every issue starts with uppercase letter.
				* Each line under 20 words.
				* Professional HSE wording.
				
				Output rules:
				* JSON only.
				* No markdown.
				* No explanation.
				* No additional fields.
				""";
	}


}