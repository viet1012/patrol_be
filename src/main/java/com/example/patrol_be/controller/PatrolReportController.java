package com.example.patrol_be.controller;

import com.example.patrol_be.dto.*;
import com.example.patrol_be.service.PatrolPivotService;
import com.example.patrol_be.service.PatrolReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patrol_report")
@RequiredArgsConstructor
@Slf4j
public class PatrolReportController {

    @Autowired
    private  PatrolReportService service;

    @Autowired
    private  PatrolPivotService pivotService;

    @GetMapping("/filter")
    public ResponseEntity<?> filter(
            @RequestParam(required = false) String plant,
            @RequestParam(required = false) String grp,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String machine,
            @RequestParam(required = false) String afStatus,
            @RequestParam(required = false) String pic,
            @RequestParam(required = false) String patrolUser,
            @RequestParam(required = false) String qrKey


    ) {

        return ResponseEntity.ok(
                service.search(plant,division, area, machine, type, grp, afStatus, pic, patrolUser,qrKey)
        );
    }

    @GetMapping("/pivot")
    public PatrolRiskPivotResponseDTO pivot(
            @RequestParam String plant,
            @RequestParam(name = "at_status")  List<String>  atStatus
    ) {
        return pivotService.getPivot(plant, atStatus);
    }


    @PutMapping("{id}/replace_image")
    public ResponseEntity<?> replaceImage(
            @PathVariable Long id,
            @RequestParam String oldImage,
            @RequestParam MultipartFile newImage
    ) throws IOException {
        String newImageName = service.replaceImage(id, oldImage, newImage);
        return ResponseEntity.ok(
                Map.of("newImage", newImageName)
        );
    }

    // ================== DELETE IMAGE ==================
    @DeleteMapping("/{id}/delete_image")
    public ResponseEntity<?> deleteImage(
            @PathVariable Long id,
            @RequestParam String image
    ) {
        service.deleteImage(id, image);
        return ResponseEntity.ok("Image deleted successfully");
    }


    // ================== ADD IMAGE ==================
    @PostMapping("/{id}/add_image")
    public ResponseEntity<?> addImage(
            @PathVariable Long id,
            @RequestParam MultipartFile image
    ) throws IOException {
        String newImageName = service.addImage(id, image);
        return ResponseEntity.ok(
                Map.of("newImage", newImageName)
        );
    }

    // ================== REPLACE IMAGE ==================
    @PutMapping("/{id}/update_at")
    public ResponseEntity<?> updateAt(
            @PathVariable Long id,
            @RequestParam("data") String dto,
            @RequestParam(value = "images", required = false)
            List<MultipartFile> images
    ) throws IOException {
        AtUpdateDTO atUpdateDTO = new ObjectMapper().readValue(dto, AtUpdateDTO.class);

        service.updateAtInfo(id, atUpdateDTO, images);
        return ResponseEntity.ok("AT updated successfully");
    }


    @PostMapping(value = "/{id}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateReport(
            @PathVariable Long id,
            @RequestParam("data") String dto,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) throws IOException {
        PatrolEditDTO atUpdateDTO = new ObjectMapper().readValue(dto, PatrolEditDTO.class);

        service.updateReport(id, atUpdateDTO, images);
        return ResponseEntity.ok().build();
    }


    @PutMapping("/{id}/hse_recheck")
    public ResponseEntity<?> hseRecheck(
            @PathVariable Long id,
            @RequestParam("data") String dto,
            @RequestParam(value = "images", required = false)
            List<MultipartFile> images
    ) throws IOException {
        HseUpdateDTO atUpdateDTO = new ObjectMapper().readValue(dto, HseUpdateDTO.class);

        service.updateHseInfo(id, atUpdateDTO, images);
        return ResponseEntity.ok("AT updated successfully");
    }

}
