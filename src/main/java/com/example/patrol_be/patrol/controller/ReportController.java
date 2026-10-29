package com.example.patrol_be.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.patrol_be.dto.QrCheckResponse;
import com.example.patrol_be.dto.ReportRequest;
import com.example.patrol_be.service.Exce;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final Exce excelService;
    private final ObjectMapper objectMapper;

    @GetMapping("/check-qr")
    public ResponseEntity<QrCheckResponse> checkQr(
            @RequestParam String qrKey
    ) {
        QrCheckResponse response = excelService.checkQr(qrKey);

        return ResponseEntity.ok(response);
    }


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> saveReport(
            @RequestParam("report") String reportJson,
            @RequestParam(value = "images", required = false)
            MultipartFile[] images
    ) throws Exception {

        ReportRequest reportRequest =
                objectMapper.readValue(
                        reportJson,
                        ReportRequest.class
                );

        excelService.appendToExcel(
                reportRequest,
                images
        );

        return ResponseEntity.ok(
                Map.of(
                        "status", "success",
                        "message", "Report saved successfully."
                )
        );
    }
}