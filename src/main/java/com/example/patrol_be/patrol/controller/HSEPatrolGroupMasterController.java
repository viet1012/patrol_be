package com.example.patrol_be.patrol.controller;

import com.example.patrol_be.patrol.dto.HSEPatrolGroupMasterDTO;
import com.example.patrol_be.patrol.dto.MachineInfoDTO;
import com.example.patrol_be.patrol.service.HSEPatrolGroupMasterService;
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

    @GetMapping("/by-macid")
    public List<MachineInfoDTO> getByMacId(@RequestParam String macId) {
        return service.findByMacId(macId);
    }
}
