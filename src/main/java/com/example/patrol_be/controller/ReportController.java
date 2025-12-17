package com.example.patrol_be.controller;
import com.example.patrol_be.dto.PatrolReportDTO;
import com.example.patrol_be.dto.ReportRequest;
import com.example.patrol_be.service.Exce;
import com.example.patrol_be.service.PatrolReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    @Autowired
//    private ExcelService excelService;
    private Exce excelService;
    private final PatrolReportService service;

    @GetMapping("/search")
    public List<PatrolReportDTO> search(
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String machine
    ) {
        return service.search(division, area, machine);
    }


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
//    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<?> saveReport(
//            @RequestParam(value = "plant", required = false) String plant,
//            @RequestParam(value = "division", required = false) String division,
//            @RequestParam(value = "area", required = false) String area,
//            @RequestParam(value = "group", required = false) String group,
//            @RequestParam(value = "machine", required = false) String machine,
//            @RequestParam(value = "comment", required = false) String comment,
//            @RequestParam(value = "check", required = false) String check,
//            @RequestParam(value = "riskFreq", required = false) String riskFreq,
//            @RequestParam(value = "riskProb", required = false) String riskProb,
//            @RequestParam(value = "riskSev", required = false) String riskSev,
//            @RequestParam(value = "riskTotal", required = false) Integer riskTotal,
//            @RequestParam(value = "images", required = false) MultipartFile[] images
//    ) {
//        try {
//            ReportRequest reportRequest = new ReportRequest();
//            reportRequest.setPlant(plant);
//            reportRequest.setDivision(division);
//            reportRequest.setArea(area);
//            reportRequest.setGroup(group);
//            reportRequest.setMachine(machine);
//            reportRequest.setComment(comment);
//            reportRequest.setCheck(check);
//            reportRequest.setRiskFreq(riskFreq);
//            reportRequest.setRiskProb(riskProb);
//            reportRequest.setRiskSev(riskSev);
//            reportRequest.setRiskTotal(riskTotal);
//            if (images != null) {
//                System.out.println("Received " + images.length + " files");
//            } else {
//                System.out.println("No files");
//            }
//
//            excelService.appendToExcel(reportRequest, images);
//
//            return ResponseEntity.ok("{\"status\":\"success\"}");
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body("{\"status\":\"error\", \"message\":\"" + e.getMessage() + "\"}");
//        }
//    }

}
