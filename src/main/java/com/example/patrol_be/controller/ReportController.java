package com.example.patrol_be.controller;

import com.example.patrol_be.dto.ReportRequest;
import com.example.patrol_be.service.Exce;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final Exce excelService;
    private final ObjectMapper objectMapper;

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