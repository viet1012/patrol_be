package com.example.patrol_be;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ExcelService excelService;

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
