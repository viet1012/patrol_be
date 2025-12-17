package com.example.patrol_be.controller;

import com.example.patrol_be.dto.HSEPatrolGroupMasterDTO;
import com.example.patrol_be.service.HSEPatrolGroupMasterService;
import lombok.*;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hse_master")
@RequiredArgsConstructor
public class HSEPatrolGroupMasterController {
    private final HSEPatrolGroupMasterService service;

    @GetMapping
    public List<HSEPatrolGroupMasterDTO> getAll() {
        return service.getAll();
    }
}
