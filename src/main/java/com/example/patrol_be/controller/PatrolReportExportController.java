package com.example.patrol_be.controller;

import com.example.patrol_be.service.PatrolReportExportExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patrol-reports")
@RequiredArgsConstructor
public class PatrolReportExportController {

    private final PatrolReportExportExcelService exportExcelService;

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String plant,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String machine,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String afStatus,
            @RequestParam(required = false) String grp,
            @RequestParam(required = false) String pic,
            @RequestParam(required = false) String patrolUser,
            @RequestParam(required = false) String qrKey
    ) throws Exception {

        byte[] bytes = exportExcelService.export(
                plant, division, area, machine, type, afStatus, grp, pic, patrolUser, qrKey
        );

        String filename = "patrol_reports.xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length)) // ⭐ BẮT BUỘC
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION + ", " + HttpHeaders.CONTENT_LENGTH)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .body(bytes);
    }
}
