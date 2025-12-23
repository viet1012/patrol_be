package com.example.patrol_be.controller;

import com.example.patrol_be.dto.PatrolReportDTO;
import com.example.patrol_be.service.PatrolReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
