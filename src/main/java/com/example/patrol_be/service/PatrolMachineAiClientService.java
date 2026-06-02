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
		payload.put("temperature", 0.0);
		payload.put("max_tokens", 1200);
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
				
				Input may contain:
				- machine
				- selectedArea
				- cate
				- sourceType
				- fac
				- division
				- totalCases
				- relatedMachines
				- comments
				
				Analysis rules:
				- Read all comments carefully.
				- Group repeated hazards by meaning.
				- Prioritize hazards that can cause serious injury.
				- Do not over-generalize different hazards.
				- Do not invent facts.
				- Do not add numbers unless clearly supported.
				- Keep machine, fac, division, area, cate unchanged.
				- Ignore broken translation notes inside comments.
				
				Important HSE priorities:
				1. Missing or damaged covers/guards.
				2. Disabled or broken interlock.
				3. Hands near grinding/rotating parts.
				4. Oil, water, or coolant near electrical parts.
				5. Slippery or unstable working platform.
				6. Unsafe wiring or socket condition.
				7. Narrow workspace and contact risk.
				8. Temporary fixing by rope, tape, plastic bag, or missing bolts.
				
				Return exactly ONE JSON object.
				Do not return multiple JSON objects.
				Do not return an array.
				Do not separate summaryVi and summaryJp into different objects.
				
				Correct:
				{
				  "summaryVi": "",
				  "summaryJp": ""
				}
				
				Wrong:
				{"summaryVi":""},{"summaryJp":""}
				
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
				- <3-10 words>
				- <3-10 words>
				
				Nguy cơ chính:
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
				- <12文字以内>
				
				主なリスク：
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
				- Use professional HSE wording.
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