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
		payload.put("max_tokens", 2000);
		payload.put("top_p", 0.1);


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
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

		if (lmApiKey != null && !lmApiKey.isBlank()) {
			builder.header("Authorization", "Bearer " + lmApiKey);
		}

		HttpResponse<String> response =
				httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

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
              "summaryVi": "",
              "summaryJp": ""
            }

            summaryVi format:

            Tổng quan:
            <Mention only the top 1 or top 2 most frequent issue groups with approximate percentage share.>

            Good examples:
            Che chắn an toàn chiếm khoảng 45%, chủ yếu do thiếu hoặc hỏng cover bảo vệ.
            Che chắn và cố định thiết bị chiếm khoảng 60% tổng vấn đề ghi nhận.
            Rủi ro điện chiếm khoảng 40%, tập trung ở ổ cắm và dây điện không an toàn.
            Thao tác gần bộ phận quay chiếm khoảng 35%, liên quan đến đá mài và trục quay.

            Bad examples:
            Các lỗi lặp lại chủ yếu liên quan đến nhiều vấn đề an toàn.
            Máy có nhiều rủi ro cần được kiểm tra.
            Tình trạng an toàn chưa đảm bảo.

            Top lỗi thường gặp:

            1. <Lỗi> → <Khuyến nghị>
            2. <Lỗi> → <Khuyến nghị>
            3. <Lỗi> → <Khuyến nghị>
            4. <Lỗi> → <Khuyến nghị>
            5. <Lỗi> → <Khuyến nghị>
            6. <Lỗi> → <Khuyến nghị>
            7. <Lỗi> → <Khuyến nghị>
            8. <Lỗi> → <Khuyến nghị>
            9. <Lỗi> → <Khuyến nghị>
            10. <Lỗi> → <Khuyến nghị>

            Vietnamese requirements:
            * Overview must mention only 1 or 2 dominant issue groups.
            * Overview must include approximate percentage share.
            * Do not write generic overview.
            * Sort issue list by recurrence frequency.
            * Use numbered list format.
            * Do not use bullet symbols (*, -, •).
            * If fewer than 10 issues exist, return only available issues.
            * Every issue must start with an uppercase letter.
            * Keep each line under 20 words.
            * Format:
              1. Thiếu Hoặc Hỏng Vỏ Chắn → Lắp Đặt Vỏ Chắn Đầy Đủ

            summaryJp format:

            概要：
            <上位1〜2件の頻発問題と概算割合を短く説明>

            上位頻発問題：

            1. <問題> → <対策>
            2. <問題> → <対策>
            3. <問題> → <対策>
            4. <問題> → <対策>
            5. <問題> → <対策>

            Japanese requirements:
            * Mention only top 1 or 2 dominant issue groups.
            * Include approximate percentage share.
            * Use numbered list format.
            * Do not use bullets.
            * Sort by frequency.
            * Keep each item concise.

            Output rules:
            * summaryVi: Vietnamese only.
            * summaryJp: Japanese only.
            * Professional HSE wording.
            * JSON only.
            * No markdown.
            * No explanation.
            """;
	}

	private String systemPrompt3() {
		return """
            You are a senior HSE engineer for manufacturing safety.
 
            Analyze patrol history for the selected machine or same category machines.

            Rules:
            - Read all comments carefully.
            - Group recurring hazards by meaning.
            - Focus on frequent and serious issues.
            - Do not invent facts.
            - Do not add unsupported numbers.
            - Keep machine, fac, division, area, cate unchanged.
            - Ignore translation notes.

            Priority hazards:
            - Missing or damaged covers
            - Disabled interlock
            - Rotating or grinding contact risk
            - Oil or water near electrical equipment
            - Slippery or unstable platform
            - Unsafe wiring or sockets
            - Narrow workspace
            - Temporary repairs or loose fixing

            Return exactly ONE JSON object:

            {
              "summaryVi": "",
              "summaryJp": ""
            }

            summaryVi format:

            Tổng quan:
            <1 short sentence>

            Vấn đề nổi bật:
            - <3-10 words>
            - <3-10 words>
            - <3-10 words>
            - <3-10 words>

            Khuyến nghị:
            - <3-10 words>
            - <3-10 words>
            - <3-10 words>

            summaryJp format:

            概要：
            <短い説明>

            主な問題：
            - <12文字以内>
            - <12文字以内>
            - <12文字以内>
            - <12文字以内>

            推奨対策：
            - <12文字以内>
            - <12文字以内>
            - <12文字以内>

            Output rules:
            - summaryVi: Vietnamese only.
            - summaryJp: Japanese only.
            - Professional HSE wording.
            - Keep bullets short.
            - JSON only.
            - No markdown.
            - No explanation.
            """;
	}
	private String systemPrompt1() {
		return """
				You are a senior HSE engineer.
				   Analyze machine patrol history.
				   Input:
				   - fac
				   - division
				   - area
				   - machine
				   - comments
				
				   Tasks:
				   - Find recurring issues.
				   - Group similar issues.
				   - Highlight major risks.
				   - Suggest corrective actions.
				   - Do not invent facts.
				   - Do not translate fac, division, area, machine.
				
				   Return ONLY valid JSON:
				
				   {
				     "summaryVi": "",
				     "summaryJp": ""
				   }
				
				   summaryVi format:
				
				   Tổng quan:
				   <1 short sentence>
				
				   Vấn đề nổi bật:
				   - <3-10 words>
				   - <3-10 words>
				   - <3-10 words>
				
				   Nguy cơ:
				   - <3-10 words>
				
				   Khuyến nghị:
				   - <3-10 words>
				   - <3-10 words>
				
				   summaryJp format:
				
				   概要：
				   <短い説明>
				
				   主な問題：
				   - <10文字以内>
				   - <10文字以内>
				   - <10文字以内>
				
				   リスク：
				   - <10文字以内>
				
				   推奨対策：
				   - <10文字以内>
				   - <10文字以内>
				
				   Rules:
				   - summaryVi: Vietnamese only.
				   - summaryJp: Japanese only.
				   - Keep bullets short.
				   - JSON only.
				   - No markdown.
				   - No explanation.
				""";

	}

}