package com.example.patrol_be.controller;

import com.example.patrol_be.dto.PatrolReportDTO;
import com.example.patrol_be.dto.UpdateReportImageDTO;
import com.example.patrol_be.service.PatrolReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/filter")
    public List<PatrolReportDTO> search(
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String machine,
            @RequestParam(required = false) String type

    ) {
        return service.search(division, area, machine, type);
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

}
