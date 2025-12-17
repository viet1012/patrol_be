package com.example.patrol_be.controller;

import com.example.patrol_be.dto.PatrolReportDTO;
import com.example.patrol_be.service.PatrolReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patrol-report")
@RequiredArgsConstructor
public class PatrolReportController {

    private final PatrolReportService service;

    @GetMapping("/search")
    public List<PatrolReportDTO> search(
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String machine
    ) {

        return service.search(division, area, machine);
    }
}
