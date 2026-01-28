
package com.example.patrol_be.controller;
import com.example.patrol_be.dto.ReportRequest;
import com.example.patrol_be.service.Exce;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    @Autowired
    private Exce excelService;



    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> saveReport(
            @RequestParam("report") String reportJson,
            @RequestParam(value = "images", required = false) MultipartFile[] images) {
        try {
            ReportRequest reportRequest = new ObjectMapper().readValue(reportJson, ReportRequest.class);
            excelService.appendToExcel(reportRequest, images);
            return ResponseEntity.ok("{\"status\":\"success\"}");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body("{\"status\":\"error\", \"message\":\"" + e.getMessage() + "\"}");
        }
    }
//

}