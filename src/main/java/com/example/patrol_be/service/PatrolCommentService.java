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
                "Translate 5S/safety patrol comments between Vietnamese ↔ Japanese automatically.\n"
                        + "Detect language first.\n"
                        + "If Japanese → translate to Vietnamese.\n"
                        + "Else → translate to natural Japanese.\n"
                        + "Return ONLY the translation.";

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
            // an toàn: kiểm tra path tồn tại trước khi lấy
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (!contentNode.isMissingNode()) {
                return contentNode.asText().trim();
            } else {
                log.warn("LLM response missing content field. body={}", resp.body());
                return null;
            }
        } else if (status == 429) {
            log.warn("LLM rate-limited (429)");
            return null;
        } else {
            log.warn("LLM returned non-200 status: {} body={}", status, resp.body());
            return null;
        }
    }
}
