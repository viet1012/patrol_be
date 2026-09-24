package com.example.patrol_be.fixedasset.service;

import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSaveRequest;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSaveResponse;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSummaryDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineLocationDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetScanInfoDto;
import com.example.patrol_be.fixedasset.repository.FixedAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FixedAssetService {
    private static final String UNKNOWN_MACHINE_NOTE_PREFIX = "NOT_FOUND_IN_MASTER";

    private final FixedAssetRepository repository;

    public FixedAssetService(FixedAssetRepository repository) {
        this.repository = repository;
    }

    public List<String> getFacs() {
        return repository.findFacs();
    }

    public List<String> getFloors(String fac) {
        return repository.findFloors(fac);
    }

    public List<String> getPositionA(String fac, String floor) {
        return repository.findPositionA(fac, floor);
    }

    public List<String> getPositionAA(String fac, String floor, String positionA) {
        return repository.findPositionAA(fac, floor, positionA);
    }

    public List<FixedAssetMachineDto> getMachines(
            String fac,
            String floor,
            String positionA,
            String positionAA
    ) {
        return repository.findMachines(fac, floor, positionA, positionAA);
    }

    public FixedAssetMachineLocationDto getMachineLocation(String machineCode) {
        String normalizedMachineCode = requireValue(machineCode, "MachineCode");
        List<FixedAssetMachineLocationDto> locations =
                repository.findMachineLocationsByCode(normalizedMachineCode);

        if (locations.isEmpty()) {
            throw new IllegalArgumentException(
                    "MachineCode not found in master: " + normalizedMachineCode);
        }

        if (locations.size() > 1) {
            throw new IllegalStateException(
                    "MachineCode has multiple master locations: " + normalizedMachineCode);
        }

        return locations.get(0);
    }

    public FixedAssetScanInfoDto getScanInfo(String machineCode) {
        String normalizedMachineCode = requireValue(machineCode, "MachineCode");
        AuditPeriod period = currentAuditPeriod();
        List<FixedAssetMachineLocationDto> locations =
                repository.findMachineLocationsByCode(normalizedMachineCode);

        if (locations.size() > 1) {
            throw new IllegalStateException(
                    "MachineCode has multiple master locations: " + normalizedMachineCode);
        }

        LocalDateTime lastAuditedAt = repository.findLatestAuditInPeriod(
                normalizedMachineCode,
                period.startDateTime(),
                period.nextStartDateTime()
        );
        FixedAssetMachineLocationDto location = locations.isEmpty() ? null : locations.get(0);

        return new FixedAssetScanInfoDto(
                normalizedMachineCode,
                location != null,
                location == null ? null : location.getFac(),
                location == null ? null : location.getFloor(),
                location == null ? null : location.getPositionA(),
                location == null ? null : location.getPositionAA(),
                location == null ? null : location.getFaName(),
                lastAuditedAt != null,
                lastAuditedAt,
                period.start(),
                period.end()
        );
    }

    public FixedAssetAuditSummaryDto getAuditSummary() {
        AuditPeriod period = currentAuditPeriod();
        long totalMachines = repository.countMasterMachines();
        long auditedMachines = repository.countAuditedMasterMachinesInPeriod(
                period.startDateTime(),
                period.nextStartDateTime()
        );
        long remainingMachines = Math.max(totalMachines - auditedMachines, 0);
        double completionPercent = totalMachines == 0
                ? 0.0
                : Math.round(auditedMachines * 10000.0 / totalMachines) / 100.0;

        return new FixedAssetAuditSummaryDto(
                period.start(),
                period.end(),
                totalMachines,
                auditedMachines,
                remainingMachines,
                completionPercent
        );
    }

    @Transactional
    public FixedAssetAuditSaveResponse saveAudit(FixedAssetAuditSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String fac = requireValue(request.getFac(), "Fac");
        String floor = requireValue(request.getFloor(), "Floor");
        String positionA = requireValue(request.getPositionA(), "PositionA");
        String positionAA = requireValue(request.getPositionAA(), "PositionAA");
        String machineCode = requireValue(request.getMachineCode(), "MachineCode");
        String userId = requireValue(request.getUserId(), "UserId");
        String userName = requireValue(request.getUserName(), "UserName");
        String note = request.getNote() == null ? null : request.getNote().trim();
        AuditPeriod period = currentAuditPeriod();

        LocalDateTime lastAuditedAt = repository.findLatestAuditInPeriodForUpdate(
                machineCode,
                period.startDateTime(),
                period.nextStartDateTime()
        );

        if (lastAuditedAt != null) {
            return new FixedAssetAuditSaveResponse(
                    true,
                    false,
                    true,
                    false,
                    machineCode,
                    null,
                    lastAuditedAt,
                    "Machine already audited in current period."
            );
        }

        if (!repository.existsLocation(fac, floor, positionA, positionAA)) {
            throw new IllegalArgumentException(
                    "Invalid location: " + fac + " / " + floor + " / " + positionA + " / " + positionAA);
        }

        boolean unknownMachine = !repository.existsMachine(machineCode);
        if (unknownMachine) {
            if (note == null || note.isBlank()) {
                note = UNKNOWN_MACHINE_NOTE_PREFIX;
            } else if (!note.contains(UNKNOWN_MACHINE_NOTE_PREFIX)) {
                note = UNKNOWN_MACHINE_NOTE_PREFIX + " | " + note;
            }
        }

        LocalDateTime updatedAt = LocalDateTime.now();

        int inserted = repository.insertAudit(
                machineCode,
                positionA,
                positionAA,
                userId,
                userName,
                updatedAt,
                note
        );

        if (inserted != 1) {
            throw new IllegalStateException("Fixed Asset audit was not saved");
        }

        return new FixedAssetAuditSaveResponse(
                true,
                true,
                false,
                unknownMachine,
                machineCode,
                updatedAt,
                null,
                unknownMachine
                        ? "Unknown machine audit saved."
                        : "Fixed Asset audit saved successfully."
        );
    }

    public List<String> getAuditedMachineCodes() {
        return repository.findAuditedMachineCodes();
    }

    private String requireValue(String value, String fieldName) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return trimmed;
    }

    private AuditPeriod currentAuditPeriod() {
        LocalDate today = LocalDate.now();
        int periodStartMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate start = LocalDate.of(today.getYear(), periodStartMonth, 1);
        LocalDate nextStart = start.plusMonths(3);

        return new AuditPeriod(
                start,
                nextStart.minusDays(1),
                start.atStartOfDay(),
                nextStart.atStartOfDay()
        );
    }

    private record AuditPeriod(
            LocalDate start,
            LocalDate end,
            LocalDateTime startDateTime,
            LocalDateTime nextStartDateTime
    ) {
    }
}
