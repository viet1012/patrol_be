package com.example.patrol_be.fixedasset.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditCheckRequest;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditCheckResponse;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveRequest;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveResponse;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSummaryDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMachineDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMachineLocationDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicDetailDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicMachineDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicSummaryDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicZoneDto;
import com.example.patrol_be.fixedasset.dto.summary.FixedAssetSummaryDto;
import com.example.patrol_be.fixedasset.dto.scan.FixedAssetQrMismatchDto;
import com.example.patrol_be.fixedasset.dto.scan.FixedAssetScanInfoDto;
import com.example.patrol_be.fixedasset.dto.zone.FixedAssetZoneLockDto;
import com.example.patrol_be.fixedasset.dto.zone.FixedAssetZoneProgressDto;
import com.example.patrol_be.fixedasset.service.FixedAssetPhotoService;
import com.example.patrol_be.fixedasset.service.FixedAssetService;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/fixed-assets")
public class FixedAssetController {
    private final FixedAssetService service;
    private final FixedAssetPhotoService photoService;

    public FixedAssetController(FixedAssetService service, FixedAssetPhotoService photoService) {
        this.service = service;
        this.photoService = photoService;
    }

    // pic (optional, EmailChecked): limits the list to that PIC's machines.
    @GetMapping("/facs")
    public List<String> getFacs(@RequestParam(required = false) String pic) {
        return service.getFacs(pic);
    }

    @GetMapping("/floors")
    public List<String> getFloors(
            @RequestParam String fac,
            @RequestParam(required = false) String pic
    ) {
        return service.getFloors(fac, pic);
    }

    @GetMapping("/position-a")
    public List<String> getPositionA(
            @RequestParam String fac,
            @RequestParam String floor,
            @RequestParam(required = false) String pic
    ) {
        return service.getPositionA(fac, floor, pic);
    }

    @GetMapping("/position-aa")
    public List<String> getPositionAA(
            @RequestParam String fac,
            @RequestParam String floor,
            @RequestParam String positionA,
            @RequestParam(required = false) String pic
    ) {
        return service.getPositionAA(fac, floor, positionA, pic);
    }

    @GetMapping("/machines")
    public List<FixedAssetMachineDto> getMachines(
            @RequestParam String fac,
            @RequestParam String floor,
            @RequestParam String positionA,
            @RequestParam String positionAA,
            @RequestParam(required = false) String pic
    ) {
        return service.getMachines(fac, floor, positionA, positionAA, pic);
    }

    @GetMapping("/pics")
    public List<FixedAssetPicDto> getPics() {
        return service.getPics();
    }

    @GetMapping("/pic-zones")
    public List<FixedAssetPicZoneDto> getPicZones(@RequestParam String pic) {
        return service.getPicZones(pic);
    }

    // period (optional): YYYY-Qn, default = current quarter.
    @GetMapping("/summary")
    public FixedAssetSummaryDto getSummary(@RequestParam(required = false) String period) {
        return service.getSummary(period);
    }

    @GetMapping("/summary/pics")
    public List<FixedAssetPicSummaryDto> getSummaryPics(
            @RequestParam(required = false) String period
    ) {
        return service.getSummaryPics(period);
    }

    // pic blank / omitted -> machines without PIC ("Chưa có PIC").
    @GetMapping("/summary/pic-detail")
    public FixedAssetPicDetailDto getSummaryPicDetail(
            @RequestParam(required = false) String pic,
            @RequestParam(required = false) String period
    ) {
        return service.getSummaryPicDetail(pic, period);
    }

    // pic blank / omitted -> machines without PIC; fac / floor / positionA /
    // positionAA (effective location) omitted -> every machine of the PIC.
    @GetMapping("/summary/pic-machines")
    public List<FixedAssetPicMachineDto> getSummaryPicMachines(
            @RequestParam(required = false) String pic,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String fac,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) String positionA,
            @RequestParam(required = false) String positionAA
    ) {
        return service.getSummaryPicMachines(pic, period, fac, floor, positionA, positionAA);
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
    public FixedAssetAuditSummaryDto getAuditSummary(@RequestParam(required = false) String pic) {
        return service.getAuditSummary(pic);
    }

    @GetMapping("/zone-progress")
    public List<FixedAssetZoneProgressDto> getZoneProgress(
            @RequestParam String fac,
            @RequestParam String floor,
            @RequestParam(required = false) String pic
    ) {
        return service.getZoneProgress(fac, floor, pic);
    }

    @GetMapping("/zone-lock")
    public FixedAssetZoneLockDto getZoneLock(@RequestParam String userId) {
        return service.getZoneLock(userId);
    }

    @GetMapping("/audited-machine-codes")
    public List<String> getAuditedMachineCodes() {
        return service.getAuditedMachineCodes();
    }

    @GetMapping("/qr-mismatch")
    public List<FixedAssetQrMismatchDto> getQrMismatchAudits(
            @RequestParam(required = false) String fac
    ) {
        return service.getQrMismatchAudits(fac);
    }

    @PostMapping("/audit-check")
    public FixedAssetAuditCheckResponse checkAudit(
            @RequestBody FixedAssetAuditCheckRequest request
    ) {
        return service.checkAudit(request);
    }

    @PostMapping("/audit")
    public FixedAssetAuditSaveResponse saveAudit(@RequestBody FixedAssetAuditSaveRequest request) {
        return service.saveAudit(request);
    }

    // "3) Thay QR" scan: part "data" = JSON body of /audit (application/json)
    // + optional part "photo" (image/jpeg, <= 5MB). 2 parts stay under Tomcat maxPartCount.
    @PostMapping(value = "/audit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FixedAssetAuditSaveResponse saveAuditWithPhoto(
            @RequestPart("data") FixedAssetAuditSaveRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {
        return photoService.saveAuditWithPhoto(request, photo);
    }
}
