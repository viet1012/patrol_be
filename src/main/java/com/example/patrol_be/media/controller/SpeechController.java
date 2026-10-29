package com.example.patrol_be.media.controller;

import com.example.patrol_be.media.service.SpeechService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

// Endpoint STT tạm tắt; bật lại bằng speech.transcribe.enabled=true
@RestController
@ConditionalOnProperty(name = "speech.transcribe.enabled", havingValue = "true")
@RequiredArgsConstructor
@RequestMapping("/api/speech")
public class SpeechController {

    private final SpeechService speechService;

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> transcribe(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam("lang") String lang,
            @RequestParam(value = "plant", required = false) String plant
    ) {
        return Map.of("text", speechService.transcribe(audio, lang, plant));
    }
}
