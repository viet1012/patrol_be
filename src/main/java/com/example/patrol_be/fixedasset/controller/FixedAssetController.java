package com.example.patrol_be.fixedasset.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSaveRequest;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSaveResponse;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSummaryDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineLocationDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetScanInfoDto;
import com.example.patrol_be.fixedasset.service.FixedAssetService;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/fixed-assets")
public class FixedAssetController {
    private final FixedAssetService service;

    public FixedAssetController(FixedAssetService service) {
        this.service = service;
    }

    @GetMapping("/facs")
    public List<String> getFacs() {
        return service.getFacs();
    }

    @GetMapping("/floors")
    public List<String> getFloors(@RequestParam String fac) {
        return service.getFloors(fac);
    }

    @GetMapping("/position-a")
    public List<String> getPositionA(
            @RequestParam String fac,
            @RequestParam String floor
    ) {
        return service.getPositionA(fac, floor);
    }

    @GetMapping("/position-aa")
    public List<String> getPositionAA(
            @RequestParam String fac,
            @RequestParam String floor,
            @RequestParam String positionA
    ) {
        return service.getPositionAA(fac, floor, positionA);
    }

    @GetMapping("/machines")
    public List<FixedAssetMachineDto> getMachines(
            @RequestParam String fac,
            @RequestParam String floor,
            @RequestParam String positionA,
            @RequestParam String positionAA
    ) {
        return service.getMachines(fac, floor, positionA, positionAA);
    }

    @GetMapping("/machine-location")
    public FixedAssetMachineLocationDto getMachineLocation(
            @RequestParam String machineCode
    ) {
        return service.getMachineLocation(machineCode);
    }

    @GetMapping("/scan-info")
    public FixedAssetScanInfoDto getScanInfo(@RequestParam String machineCode) {
        return service.getScanInfo(machineCode);
    }

    @GetMapping("/audit-summary")
    public FixedAssetAuditSummaryDto getAuditSummary() {
        return service.getAuditSummary();
    }

    @GetMapping("/audited-machine-codes")
    public List<String> getAuditedMachineCodes() {
        return service.getAuditedMachineCodes();
    }

    @PostMapping("/audit")
    public FixedAssetAuditSaveResponse saveAudit(@RequestBody FixedAssetAuditSaveRequest request) {
        return service.saveAudit(request);
    }
}
