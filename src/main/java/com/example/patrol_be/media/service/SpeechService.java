package com.example.patrol_be.media.service;

import com.example.patrol_be.common.exception.SpeechServiceUnavailableException;
import com.example.patrol_be.patrol.repository.HSEPatrolGroupMasterRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class SpeechService {

	public static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

	private static final Set<String> SUPPORTED_LANGS = Set.of("vi", "en", "ja");
	private static final int GLOSSARY_LIMIT = 200;
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private final HSEPatrolGroupMasterRepo masterRepo;
	private final RestClient restClient;
	private final String sttUrl;

	public SpeechService(
			HSEPatrolGroupMasterRepo masterRepo,
			@Value("${speech.stt-url:http://127.0.0.1:7011}") String sttUrl
	) {
		this.masterRepo = masterRepo;
		this.sttUrl = sttUrl.endsWith("/") ? sttUrl.substring(0, sttUrl.length() - 1) : sttUrl;

		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(TIMEOUT);
		factory.setReadTimeout(TIMEOUT);

		this.restClient = RestClient.builder()
				.baseUrl(this.sttUrl)
				.requestFactory(factory)
				.build();

		log.info("STT service URL: {}/transcribe", this.sttUrl);
	}

	// ===============================
	// AUDIO → TEXT
	// ===============================
	public String transcribe(MultipartFile audio, String lang, String plant) {

		if (audio == null || audio.isEmpty()) {
			throw new IllegalArgumentException("File audio trống");
		}
		if (audio.getSize() > MAX_AUDIO_BYTES) {
			throw new IllegalArgumentException("File audio vượt quá 10MB");
		}

		String language = lang == null ? "" : lang.trim().toLowerCase();
		if (!SUPPORTED_LANGS.contains(language)) {
			throw new IllegalArgumentException("lang không hợp lệ (vi|en|ja)");
		}

		String glossary = buildGlossary(plant);

		byte[] bytes;
		try {
			bytes = audio.getBytes();
		} catch (IOException e) {
			throw new IllegalArgumentException("Không đọc được file audio");
		}

		String filename = audio.getOriginalFilename() != null && !audio.getOriginalFilename().isBlank()
				? audio.getOriginalFilename()
				: "audio";

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		body.add("language", language);
		body.add("prompt", glossary);

		Map<?, ?> res;
		try {
			res = restClient.post()
					.uri("/transcribe")
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(body)
					.retrieve()
					.body(Map.class);
		} catch (Exception e) {
			Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
			log.error("STT call failed | url={}/transcribe | lang={} | plant={} | size={} | rootCause={}: {}",
					sttUrl, language, plant, audio.getSize(),
					root.getClass().getName(), root.getMessage(), e);
			throw new SpeechServiceUnavailableException(e);
		}

		if (res == null || !(res.get("text") instanceof String text)) {
			log.error("STT invalid response: {}", res);
			throw new SpeechServiceUnavailableException();
		}

		log.info("STT done | lang={} | plant={} | size={} | chars={}",
				language, plant, audio.getSize(), text.length());

		return text.trim();
	}

	// ===============================
	// GLOSSARY: division + khu vực + tên máy
	// ===============================
	private String buildGlossary(String plant) {
		String p = plant == null || plant.isBlank() ? null : plant.trim();
		try {
			List<String> terms = masterRepo.findGlossaryTerms(p, GLOSSARY_LIMIT);
			return String.join(", ", terms);
		} catch (Exception e) {
			// Glossary chỉ hỗ trợ độ chính xác, lỗi DB không chặn nhận diện
			log.warn("Build STT glossary failed | plant={}", p, e);
			return "";
		}
	}
}
