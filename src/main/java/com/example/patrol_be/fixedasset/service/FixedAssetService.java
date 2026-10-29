package com.example.patrol_be.fixedasset.service;

import com.example.patrol_be.fixedasset.dto.FixedAssetAuditCheckRequest;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditCheckResponse;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSaveRequest;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSaveResponse;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSummaryDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineLocationDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMasterMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetLatestAuditDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetLatestAutoAuditDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetQrMismatchDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetZoneLockDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetScanInfoDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetResolvedLocationDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetZoneProgressDto;
import com.example.patrol_be.fixedasset.repository.FixedAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class FixedAssetService {
    private static final String UNKNOWN_MACHINE_NOTE_PREFIX = "NOT_FOUND_IN_MASTER";
    private static final String LOCATION_MISMATCH_NOTE_PREFIX = "LOCATION_MISMATCH";
    private static final String ACTUAL_LOCATION_NOT_MAPPED_NOTE_PREFIX =
            "ACTUAL_LOCATION_NOT_MAPPED";
    private static final String NO_QR_MODE = "NO_QR";
    private static final String NO_QR_NOTE_PREFIX = "NO_QR";
    private static final Set<String> AUDIT_MODES = Set.of("AUTO", "MANUAL", NO_QR_MODE);
    private static final String ZONE_LOCK_INCOMPLETE = "INCOMPLETE";
    private static final String ZONE_LOCK_COMPLETED = "COMPLETED";
    private static final String ZONE_LOCK_NO_AUTO_AUDIT = "NO_AUTO_AUDIT";
    private static final String ZONE_LOCK_UNRESOLVED = "UNRESOLVED";

    private final FixedAssetRepository repository;

    public FixedAssetService(FixedAssetRepository repository) {
        this.repository = repository;
    }

    public List<String> getFacs() {
        return repository.findFacs();
    }

    public List<String> getFloors(String fac) {
        return repository.findFloors(requireValue(fac, "Fac"));
    }

    public List<String> getPositionA(String fac, String floor) {
        return repository.findPositionA(
                requireValue(fac, "Fac"),
                requireValue(floor, "Floor")
        );
    }

    public List<String> getPositionAA(String fac, String floor, String positionA) {
        return repository.findPositionAA(
                requireValue(fac, "Fac"),
                requireValue(floor, "Floor"),
                requireValue(positionA, "PositionA")
        );
    }

    public List<FixedAssetMachineDto> getMachines(
            String fac,
            String floor,
            String positionA,
            String positionAA
    ) {
        String normalizedFac = requireValue(fac, "Fac");
        String normalizedFloor = requireValue(floor, "Floor");
        String normalizedPositionA = requireValue(positionA, "PositionA");
        String normalizedPositionAA = requireValue(positionAA, "PositionAA");
        AuditPeriod period = currentAuditPeriod();

        return repository.findMachines(
                normalizedFac,
                normalizedFloor,
                normalizedPositionA,
                normalizedPositionAA,
                period.startDateTime(),
                period.nextStartDateTime()
        );
    }

    public FixedAssetMachineLocationDto getMachineLocation(String machineCode) {
        String normalizedMachineCode = requireValue(machineCode, "MachineCode");
        MasterMachine master = findMaster(normalizedMachineCode);

        if (master == null) {
            throw new IllegalArgumentException(
                    "MachineCode not found in master: " + normalizedMachineCode);
        }

        // Machine exists, but its MASTER location has no MAP row: do not
        // pretend it is missing and do not invent Fac.
        if (!master.mappingValid()) {
            throw new IllegalStateException(
                    "Master location is not mapped: " + normalizedMachineCode + " ("
                            + noteValue(master.floor()) + "/"
                            + noteValue(master.positionA()) + "/"
                            + noteValue(master.positionAA()) + ")");
        }

        return new FixedAssetMachineLocationDto(
                master.fac(),
                master.floor(),
                master.positionA(),
                master.positionAA(),
                master.machineCode(),
                master.faName()
        );
    }

    public FixedAssetScanInfoDto getScanInfo(String machineCode) {
        String normalizedMachineCode = requireValue(machineCode, "MachineCode");
        AuditPeriod period = currentAuditPeriod();
        MasterMachine master = findMaster(normalizedMachineCode);

        FixedAssetLatestAuditDto latestAudit = repository.findLatestAuditInPeriod(
                normalizedMachineCode,
                period.startDateTime(),
                period.nextStartDateTime()
        );
        FixedAssetLatestAuditDto reprint = qrReprintRow(latestAudit);

        return new FixedAssetScanInfoDto(
                normalizedMachineCode,
                master == null ? null : master.div(),
                master != null,
                master != null && master.locationResolved(),
                master != null && master.mappingValid(),
                master == null ? null : master.fac(),
                master == null ? null : master.floor(),
                master == null ? null : master.positionA(),
                master == null ? null : master.positionAA(),
                master == null ? null : master.faName(),
                isAudited(latestAudit),
                latestAudit != null && latestAudit.isNeedsQrReprint(),
                latestAudit == null ? null : latestAudit.getUpdatedAt(),
                latestAudit == null ? null : latestAudit.getUserId(),
                latestAudit == null ? null : latestAudit.getUserName(),
                period.start(),
                period.end(),
                reprint == null ? null : reprint.getFacAct(),
                reprint == null ? null : reprint.getFloorAct(),
                reprint == null ? null : reprint.getPositionAAct(),
                reprint == null ? null : reprint.getPositionAAAct(),
                reprint == null ? null : reprint.getQrFac(),
                reprint == null ? null : reprint.getQrFloor(),
                reprint == null ? null : reprint.getQrPositionA(),
                reprint == null ? null : reprint.getQrPositionAA()
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

    public List<FixedAssetZoneProgressDto> getZoneProgress(String fac, String floor) {
        AuditPeriod period = currentAuditPeriod();
        return repository.findZoneProgress(
                requireValue(fac, "Fac"),
                requireValue(floor, "Floor"),
                period.startDateTime(),
                period.nextStartDateTime()
        );
    }

    /**
     * Current Auto zone lock, derived from the user's latest AUTO audit in the
     * period. Progress comes from findZoneProgress so it always equals
     * /zone-progress for the same zone.
     */
    public FixedAssetZoneLockDto getZoneLock(String userId) {
        String normalizedUserId = requireValue(userId, "UserId");
        AuditPeriod period = currentAuditPeriod();

        FixedAssetLatestAutoAuditDto latestAuto = repository.findLatestAutoAuditInPeriod(
                normalizedUserId,
                period.startDateTime(),
                period.nextStartDateTime()
        );
        if (latestAuto == null) {
            return new FixedAssetZoneLockDto(
                    false, ZONE_LOCK_NO_AUTO_AUDIT, null, null, null, null, null, null, null);
        }

        List<FixedAssetResolvedLocationDto> locations = repository.findLocationsByPositionAAndAA(
                latestAuto.getPositionA(),
                latestAuto.getPositionAA()
        );
        // (A, AA) is not unique across Fac/Floor in MAP (e.g. Fac_A 1F/2F):
        // never guess the floor.
        if (locations.size() != 1) {
            return new FixedAssetZoneLockDto(
                    false, ZONE_LOCK_UNRESOLVED, null, null, null, null, null, null,
                    latestAuto.getUpdatedAt());
        }

        FixedAssetResolvedLocationDto location = locations.get(0);
        FixedAssetZoneProgressDto progress = repository.findZoneProgress(
                        location.getFac(),
                        location.getFloor(),
                        period.startDateTime(),
                        period.nextStartDateTime()
                ).stream()
                .filter(zone -> sameZoneValue(zone.getPositionA(), location.getPositionA())
                        && sameZoneValue(zone.getPositionAA(), location.getPositionAA()))
                .findFirst()
                .orElse(null);

        long total = progress == null ? 0 : progress.getTotal();
        long audited = progress == null ? 0 : progress.getAudited();
        boolean incomplete = total > 0 && audited < total;

        // Auto zone lock disabled: Auto audit is allowed in any zone, so
        // locked is always false. Progress fields are kept for display only.
        return new FixedAssetZoneLockDto(
                false,
                incomplete ? ZONE_LOCK_INCOMPLETE : ZONE_LOCK_COMPLETED,
                location.getFac(),
                location.getFloor(),
                location.getPositionA(),
                location.getPositionAA(),
                total,
                audited,
                latestAuto.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public FixedAssetAuditCheckResponse checkAudit(FixedAssetAuditCheckRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String machineCode = requireValue(request.getMachineCode(), "MachineCode");
        String floor = requireValue(request.getFloor(), "Floor");
        String positionAA = requireValue(request.getPositionAA(), "PositionAA");
        AuditPeriod period = currentAuditPeriod();

        FixedAssetLatestAuditDto latestAudit = repository.findLatestAuditInPeriod(
                machineCode,
                period.startDateTime(),
                period.nextStartDateTime()
        );
        MasterMachine master = findMaster(machineCode);
        FixedAssetResolvedLocationDto actualLocation =
                resolveActualLocation(floor, positionAA);

        if (actualLocation == null && master == null) {
            throw new IllegalArgumentException(
                    "Actual location not found: " + floor + " / " + positionAA);
        }

        // Existence from F2_FIXED_ASSET only; MAP failure never means "unknown".
        boolean existsInMaster = master != null;
        boolean actualLocationResolved = actualLocation != null;
        boolean locationMatch = existsInMaster
                && actualLocationResolved
                && locationsMatch(master, actualLocation);
        boolean alreadyAudited = isAudited(latestAudit);
        FixedAssetLatestAuditDto reprint = qrReprintRow(latestAudit);
        boolean requiresConfirmation = !alreadyAudited && existsInMaster && !locationMatch;
        String message;

        if (alreadyAudited) {
            message = "Machine already audited in current period.";
        } else if (existsInMaster && !actualLocationResolved) {
            message = "Actual location not found in MAP: " + floor + " / " + positionAA;
        } else if (!existsInMaster) {
            message = "MachineCode not found in master.";
        } else if (locationMatch) {
            message = "Machine location matches master.";
        } else {
            message = "Machine location does not match master.";
        }

        return new FixedAssetAuditCheckResponse(
                machineCode,
                alreadyAudited,
                latestAudit != null && latestAudit.isNeedsQrReprint(),
                latestAudit == null ? null : latestAudit.getUpdatedAt(),
                latestAudit == null ? null : latestAudit.getUserId(),
                latestAudit == null ? null : latestAudit.getUserName(),
                existsInMaster,
                existsInMaster && master.locationResolved(),
                existsInMaster && master.mappingValid(),
                actualLocationResolved,
                locationMatch,
                requiresConfirmation,
                existsInMaster ? master.fac() : null,
                existsInMaster ? master.floor() : null,
                existsInMaster ? master.positionA() : null,
                existsInMaster ? master.positionAA() : null,
                existsInMaster ? master.faName() : null,
                actualLocation == null ? null : actualLocation.getFac(),
                actualLocation == null ? floor : actualLocation.getFloor(),
                actualLocation == null ? null : actualLocation.getPositionA(),
                actualLocation == null ? positionAA : actualLocation.getPositionAA(),
                message,
                reprint == null ? null : reprint.getFacAct(),
                reprint == null ? null : reprint.getFloorAct(),
                reprint == null ? null : reprint.getPositionAAct(),
                reprint == null ? null : reprint.getPositionAAAct(),
                reprint == null ? null : reprint.getQrFac(),
                reprint == null ? null : reprint.getQrFloor(),
                reprint == null ? null : reprint.getQrPositionA(),
                reprint == null ? null : reprint.getQrPositionAA()
        );
    }

    @Transactional
    public FixedAssetAuditSaveResponse saveAudit(FixedAssetAuditSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String fac = request.getFac() == null ? null : request.getFac().trim();
        String floor = requireValue(request.getFloor(), "Floor");
        String positionA = request.getPositionA() == null ? null : request.getPositionA().trim();
        String positionAA = requireValue(request.getPositionAA(), "PositionAA");
        String machineCode = requireValue(request.getMachineCode(), "MachineCode");
        String userId = requireValue(request.getUserId(), "UserId");
        String userName = requireValue(request.getUserName(), "UserName");
        String note = request.getNote() == null ? null : request.getNote().trim();
        String auditMode = normalizeAuditMode(request.getMode());
        // NO_QR: machine has no QR label (form "Ghi nhận máy thiếu QR").
        boolean noQr = NO_QR_MODE.equals(auditMode);
        // QR label location (Manual mode); stored as sent, null -> not mismatched.
        String qrFloor = trimToNull(request.getQrFloor());
        String qrPositionA = trimToNull(request.getQrPositionA());
        String qrPositionAA = trimToNull(request.getQrPositionAA());
        // Fac_Qr (and A_Qr when FE omits it) from MAP; left null when not unique.
        FixedAssetResolvedLocationDto qrLocation =
                resolveUniqueLocation(qrFloor, qrPositionA, qrPositionAA);
        String qrFac = qrLocation == null ? null : qrLocation.getFac();
        if (qrPositionA == null && qrLocation != null) {
            qrPositionA = qrLocation.getPositionA();
        }
        boolean qrMismatch = Boolean.TRUE.equals(request.getQrLocationMismatch())
                || Boolean.TRUE.equals(request.getNeedsQrReprint());
        AuditPeriod period = currentAuditPeriod();

        // Locked read of the latest row; any row in the period (QrMismatch or
        // not) means audited, so at most one audit row per machine per period.
        FixedAssetLatestAuditDto latestAudit = repository.findLatestAuditInPeriodForUpdate(
                machineCode,
                period.startDateTime(),
                period.nextStartDateTime()
        );

        if (isAudited(latestAudit)) {
            // Rescan of a flagged machine: decided by the scanned QR label
            // (still under the UPDLOCK above), no new row. NO_QR keeps the
            // mark branch below.
            if (latestAudit.isNeedsQrReprint() && !noQr) {
                return rescanQrReprint(
                        latestAudit,
                        machineCode,
                        fac,
                        floor,
                        positionA,
                        positionAA,
                        qrFac,
                        qrFloor,
                        qrPositionA,
                        qrPositionAA,
                        Boolean.TRUE.equals(request.getConfirmLocationMismatch())
                );
            }

            // QR mismatch on an already audited machine: flag the existing row
            // for reprint instead of inserting a duplicate.
            if (qrMismatch) {
                int updated = repository.markAuditQrReprint(
                        latestAudit.getId(),
                        // only NO_QR overwrites the audited row's mode
                        noQr ? auditMode : null,
                        qrFac,
                        qrFloor,
                        qrPositionA,
                        qrPositionAA
                );
                if (updated != 1) {
                    throw new IllegalStateException("Fixed Asset QR reprint flag was not saved");
                }
            }

            // Row state after the save: mark keeps old *_Qr where nothing was
            // sent (COALESCE), otherwise the row is unchanged.
            boolean rowFlagged = qrMismatch || latestAudit.isNeedsQrReprint();
            return new FixedAssetAuditSaveResponse(
                    true,
                    false,
                    true,
                    qrMismatch,
                    false,
                    false,
                    false,
                    machineCode,
                    null,
                    latestAudit.getUpdatedAt(),
                    latestAudit.getUserId(),
                    latestAudit.getUserName(),
                    null,
                    null,
                    null,
                    null,
                    fac,
                    floor,
                    positionA,
                    positionAA,
                    qrMismatch
                            ? "Machine already audited in current period. Marked for QR reprint."
                            : "Machine already audited in current period.",
                    false,
                    !rowFlagged ? null : qrMismatch && qrFac != null ? qrFac : latestAudit.getQrFac(),
                    !rowFlagged ? null : qrMismatch && qrFloor != null ? qrFloor : latestAudit.getQrFloor(),
                    !rowFlagged ? null : qrMismatch && qrPositionA != null
                            ? qrPositionA : latestAudit.getQrPositionA(),
                    !rowFlagged ? null : qrMismatch && qrPositionAA != null
                            ? qrPositionAA : latestAudit.getQrPositionAA()
            );
        }

        MasterMachine master = findMaster(machineCode);
        // NO_QR picks all 4 levels from the MAP cascade (validated by
        // existsLocation below): no Floor + PositionAA resolve, so Fac_Act /
        // A_Act are never dropped as "not in MAP".
        boolean fullLocationGiven = noQr && hasText(fac) && hasText(positionA);

        if (master != null && !fullLocationGiven) {
            FixedAssetResolvedLocationDto resolvedActual =
                    resolveActualLocation(floor, positionAA);

            if (resolvedActual == null) {
                if (!Boolean.TRUE.equals(request.getConfirmLocationMismatch())) {
                    return new FixedAssetAuditSaveResponse(
                            false,
                            false,
                            false,
                            false,
                            false,
                            true,
                            true,
                            machineCode,
                            null,
                            null,
                            null,
                            null,
                            master.fac(),
                            master.floor(),
                            master.positionA(),
                            master.positionAA(),
                            null,
                            floor,
                            null,
                            positionAA,
                            "Actual location not found in MAP. Confirmation required.",
                            false,
                            null,
                            null,
                            null,
                            null
                    );
                }

                note = unmappedActualNote(master, floor, positionAA, note);
                if (noQr) {
                    note = noQrNote(note);
                }
                LocalDateTime updatedAt = LocalDateTime.now();
                int inserted = repository.insertAudit(
                        machineCode,
                        null,
                        floor,
                        null,
                        positionAA,
                        userId,
                        userName,
                        updatedAt,
                        note,
                        auditMode,
                        qrFac,
                        qrFloor,
                        qrPositionA,
                        qrPositionAA,
                        qrMismatch
                );

                if (inserted != 1) {
                    throw new IllegalStateException("Fixed Asset audit was not saved");
                }

                return new FixedAssetAuditSaveResponse(
                        true,
                        true,
                        false,
                        qrMismatch,
                        false,
                        true,
                        false,
                        machineCode,
                        updatedAt,
                        null,
                        null,
                        null,
                        master.fac(),
                        master.floor(),
                        master.positionA(),
                        master.positionAA(),
                        null,
                        floor,
                        null,
                        positionAA,
                        "Audit saved with unmapped actual location.",
                        false,
                        qrMismatch ? qrFac : null,
                        qrMismatch ? qrFloor : null,
                        qrMismatch ? qrPositionA : null,
                        qrMismatch ? qrPositionAA : null
                );
            }
        }

        fac = requireValue(fac, "Fac");
        positionA = requireValue(positionA, "PositionA");
        if (!repository.existsLocation(fac, floor, positionA, positionAA)) {
            throw new IllegalArgumentException(
                    "Invalid location: " + fac + " / " + floor + " / " + positionA + " / " + positionAA);
        }

        // Same rule as audit-check: unknown ONLY when F2_FIXED_ASSET has no row.
        boolean unknownMachine = master == null;
        boolean locationMismatch = !unknownMachine && !locationsMatch(
                master,
                new FixedAssetResolvedLocationDto(fac, floor, positionA, positionAA)
        );

        if (locationMismatch && !Boolean.TRUE.equals(request.getConfirmLocationMismatch())) {
            return new FixedAssetAuditSaveResponse(
                    true,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    machineCode,
                    null,
                    null,
                    null,
                    null,
                    master.fac(),
                    master.floor(),
                    master.positionA(),
                    master.positionAA(),
                    fac,
                    floor,
                    positionA,
                    positionAA,
                    "Machine location does not match master.",
                    false,
                    null,
                    null,
                    null,
                    null
            );
        }

        if (unknownMachine) {
            if (note == null || note.isBlank()) {
                note = UNKNOWN_MACHINE_NOTE_PREFIX;
            } else if (!note.contains(UNKNOWN_MACHINE_NOTE_PREFIX)) {
                note = UNKNOWN_MACHINE_NOTE_PREFIX + " | " + note;
            }
        } else if (locationMismatch) {
            note = mismatchNote(master, fac, floor, positionA, positionAA, note);
        }
        if (noQr) {
            note = noQrNote(note);
        }

        LocalDateTime updatedAt = LocalDateTime.now();

        // *_Act = ACTUAL location of this audit. MASTER is never modified.
        int inserted = repository.insertAudit(
                machineCode,
                fac,
                floor,
                positionA,
                positionAA,
                userId,
                userName,
                updatedAt,
                note,
                auditMode,
                qrFac,
                qrFloor,
                qrPositionA,
                qrPositionAA,
                qrMismatch
        );

        if (inserted != 1) {
            throw new IllegalStateException("Fixed Asset audit was not saved");
        }

        return new FixedAssetAuditSaveResponse(
                true,
                true,
                false,
                qrMismatch,
                unknownMachine,
                locationMismatch,
                false,
                machineCode,
                updatedAt,
                null,
                null,
                null,
                unknownMachine ? null : master.fac(),
                unknownMachine ? null : master.floor(),
                unknownMachine ? null : master.positionA(),
                unknownMachine ? null : master.positionAA(),
                fac,
                floor,
                positionA,
                positionAA,
                unknownMachine
                        ? "Unknown machine audit saved."
                        : "Fixed Asset audit saved successfully.",
                false,
                qrMismatch ? qrFac : null,
                qrMismatch ? qrFloor : null,
                qrMismatch ? qrPositionA : null,
                qrMismatch ? qrPositionAA : null
        );
    }

    public List<String> getAuditedMachineCodes() {
        return repository.findAuditedMachineCodes();
    }

    public List<FixedAssetQrMismatchDto> getQrMismatchAudits(String fac) {
        AuditPeriod period = currentAuditPeriod();
        return repository.findQrMismatchAudits(
                trimToNull(fac),
                period.startDateTime(),
                period.nextStartDateTime()
        );
    }

    /** Audited = any row in the period; QrMismatch = 1 is only a QR label status. */
    private boolean isAudited(FixedAssetLatestAuditDto latestAudit) {
        return latestAudit != null;
    }

    /** Latest row when it still needs QR reprint, else null (reprint* fields stay null). */
    private FixedAssetLatestAuditDto qrReprintRow(FixedAssetLatestAuditDto latestAudit) {
        return latestAudit != null && latestAudit.isNeedsQrReprint() ? latestAudit : null;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String requireValue(String value, String fieldName) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return trimmed;
    }

    private FixedAssetResolvedLocationDto resolveActualLocation(String floor, String positionAA) {
        List<FixedAssetResolvedLocationDto> locations =
                repository.findLocationsByFloorAndPositionAA(floor, positionAA);

        if (locations.isEmpty()) {
            return null;
        }
        if (locations.size() > 1) {
            throw new IllegalStateException(
                    "Actual location is ambiguous: " + floor + " / " + positionAA);
        }
        return locations.get(0);
    }

    /**
     * Unique MAP row for Floor + PositionAA (+ PositionA when given), else null
     * (no throw, no guess). Used for the QR label and the selected location.
     */
    private FixedAssetResolvedLocationDto resolveUniqueLocation(
            String floor,
            String positionA,
            String positionAA) {
        if (!hasText(floor) || !hasText(positionAA)) {
            return null;
        }

        List<FixedAssetResolvedLocationDto> locations =
                repository.findLocationsByFloorAndPositionAA(floor, positionAA).stream()
                        .filter(location -> !hasText(positionA)
                                || positionA.equals(location.getPositionA()))
                        .toList();

        return locations.size() == 1 ? locations.get(0) : null;
    }

    /**
     * Rescan of a machine whose latest row has QrMismatch = 1, decided by the
     * scanned QR label location (qr*):
     * - label = old *_Qr -> label not reprinted yet, nothing changes;
     * - label = *_Act and selected = *_Act (NO_QR row: label = *_Act) -> clear,
     *   scanned label stored as *_Qr;
     * - otherwise the machine may have moved -> confirmation, then *_Act and
     *   *_Qr are overwritten and QrMismatch stays 1.
     */
    private FixedAssetAuditSaveResponse rescanQrReprint(
            FixedAssetLatestAuditDto latest,
            String machineCode,
            String fac,
            String floor,
            String positionA,
            String positionAA,
            String qrFac,
            String qrFloor,
            String qrPositionA,
            String qrPositionAA,
            boolean confirmed) {
        if (qrFloor == null || qrPositionAA == null) {
            throw new IllegalArgumentException(
                    "QrFloor and QrPositionAA are required: machine " + machineCode
                            + " is waiting for QR reprint.");
        }

        // Selected location: Fac / PositionA filled from MAP when not sent.
        FixedAssetResolvedLocationDto selected =
                resolveUniqueLocation(floor, positionA, positionAA);
        if (selected != null) {
            fac = hasText(fac) ? fac : selected.getFac();
            positionA = hasText(positionA) ? positionA : selected.getPositionA();
        }

        boolean noQrRow = !hasText(latest.getQrFloor()) && !hasText(latest.getQrPositionAA());
        // Row *_Qr columns that are NULL (legacy rows without A_Qr / Fac_Qr)
        // are skipped; Floor_Qr + AA_Qr must always match.
        boolean labelIsOld = !noQrRow
                && sameZoneValue(qrFloor, latest.getQrFloor())
                && sameZoneValue(qrPositionAA, latest.getQrPositionAA())
                && (!hasText(latest.getQrPositionA())
                        || sameZoneValue(qrPositionA, latest.getQrPositionA()))
                && (!hasText(latest.getQrFac()) || !hasText(qrFac)
                        || sameZoneValue(qrFac, latest.getQrFac()));
        boolean labelIsActual = sameAuditLocation(
                qrFac, qrFloor, qrPositionA, qrPositionAA,
                latest.getFacAct(), latest.getFloorAct(),
                latest.getPositionAAct(), latest.getPositionAAAct());
        boolean selectedIsActual = sameAuditLocation(
                fac, floor, positionA, positionAA,
                latest.getFacAct(), latest.getFloorAct(),
                latest.getPositionAAct(), latest.getPositionAAAct());

        if (labelIsOld) {
            return new FixedAssetAuditSaveResponse(
                    true,
                    false,
                    true,
                    true,
                    false,
                    false,
                    false,
                    machineCode,
                    null,
                    latest.getUpdatedAt(),
                    latest.getUserId(),
                    latest.getUserName(),
                    null,
                    null,
                    null,
                    null,
                    latest.getFacAct(),
                    latest.getFloorAct(),
                    latest.getPositionAAct(),
                    latest.getPositionAAAct(),
                    "Tem chưa được in lại",
                    true,
                    latest.getQrFac(),
                    latest.getQrFloor(),
                    latest.getQrPositionA(),
                    latest.getQrPositionAA()
            );
        }

        if (labelIsActual && (noQrRow || selectedIsActual)) {
            // qrFac / qrPositionA already filled from MAP when the label lacks them.
            int updated = repository.clearAuditQrReprint(
                    latest.getId(),
                    qrFac,
                    qrFloor,
                    qrPositionA,
                    qrPositionAA
            );
            if (updated != 1) {
                throw new IllegalStateException("Fixed Asset QR reprint flag was not cleared");
            }

            return new FixedAssetAuditSaveResponse(
                    true,
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                    machineCode,
                    null,
                    latest.getUpdatedAt(),
                    latest.getUserId(),
                    latest.getUserName(),
                    null,
                    null,
                    null,
                    null,
                    latest.getFacAct(),
                    latest.getFloorAct(),
                    latest.getPositionAAct(),
                    latest.getPositionAAAct(),
                    "QR khớp vị trí, đã gỡ đánh dấu in lại QR.",
                    false,
                    qrFac,
                    qrFloor,
                    qrPositionA,
                    qrPositionAA
            );
        }

        if (!confirmed) {
            return new FixedAssetAuditSaveResponse(
                    true,
                    false,
                    true,
                    true,
                    false,
                    true,
                    true,
                    machineCode,
                    null,
                    latest.getUpdatedAt(),
                    latest.getUserId(),
                    latest.getUserName(),
                    null,
                    null,
                    null,
                    null,
                    fac,
                    floor,
                    positionA,
                    positionAA,
                    "Vị trí tem / vị trí chọn khác vị trí thực tế đã ghi nhận "
                            + "(máy có thể đã dời). Cần xác nhận.",
                    false,
                    latest.getQrFac(),
                    latest.getQrFloor(),
                    latest.getQrPositionA(),
                    latest.getQrPositionAA()
            );
        }

        fac = requireValue(fac, "Fac");
        positionA = requireValue(positionA, "PositionA");
        if (!repository.existsLocation(fac, floor, positionA, positionAA)) {
            throw new IllegalArgumentException(
                    "Invalid location: " + fac + " / " + floor + " / " + positionA + " / " + positionAA);
        }

        int updated = repository.relocateAuditQrReprint(
                latest.getId(),
                fac,
                floor,
                positionA,
                positionAA,
                qrFac,
                qrFloor,
                qrPositionA,
                qrPositionAA
        );
        if (updated != 1) {
            throw new IllegalStateException("Fixed Asset audit location was not updated");
        }

        return new FixedAssetAuditSaveResponse(
                true,
                false,
                true,
                true,
                false,
                true,
                false,
                machineCode,
                null,
                latest.getUpdatedAt(),
                latest.getUserId(),
                latest.getUserName(),
                null,
                null,
                null,
                null,
                fac,
                floor,
                positionA,
                positionAA,
                "Đã cập nhật vị trí thực tế và vị trí tem, vẫn cần in lại QR.",
                false,
                qrFac,
                qrFloor,
                qrPositionA,
                qrPositionAA
        );
    }

    /** Trimmed, case-insensitive; Fac compared only when both sides have a value. */
    private boolean sameAuditLocation(
            String fac1,
            String floor1,
            String positionA1,
            String positionAA1,
            String fac2,
            String floor2,
            String positionA2,
            String positionAA2) {
        return (!hasText(fac1) || !hasText(fac2) || sameZoneValue(fac1, fac2))
                && sameZoneValue(floor1, floor2)
                && sameZoneValue(positionA1, positionA2)
                && sameZoneValue(positionAA1, positionAA2);
    }

    /**
     * NO_QR marker always first, before LOCATION_MISMATCH /
     * NOT_FOUND_IN_MASTER / ACTUAL_LOCATION_NOT_MAPPED; never added twice.
     */
    private String noQrNote(String note) {
        if (note == null || note.isBlank()) {
            return NO_QR_NOTE_PREFIX;
        }
        if (note.startsWith(NO_QR_NOTE_PREFIX)) {
            return note;
        }
        return NO_QR_NOTE_PREFIX + " | " + note;
    }

    private String unmappedActualNote(
            MasterMachine master,
            String scannedFloor,
            String scannedPositionAA,
            String originalNote
    ) {
        String masterLocation = String.join("/",
                noteValue(master.fac()),
                noteValue(master.floor()),
                noteValue(master.positionA()),
                noteValue(master.positionAA()));
        String metadata = "MASTER=" + masterLocation
                + " | SCANNED=" + scannedFloor + "/" + scannedPositionAA;

        if (originalNote == null || originalNote.isBlank()) {
            return ACTUAL_LOCATION_NOT_MAPPED_NOTE_PREFIX + " | " + metadata;
        }
        if (originalNote.contains(ACTUAL_LOCATION_NOT_MAPPED_NOTE_PREFIX)) {
            String noteWithoutMarker = originalNote
                    .replace(ACTUAL_LOCATION_NOT_MAPPED_NOTE_PREFIX, "")
                    .trim();
            return noteWithoutMarker.isEmpty()
                    ? ACTUAL_LOCATION_NOT_MAPPED_NOTE_PREFIX + " | " + metadata
                    : ACTUAL_LOCATION_NOT_MAPPED_NOTE_PREFIX + " | " + metadata
                            + " | " + noteWithoutMarker;
        }
        return ACTUAL_LOCATION_NOT_MAPPED_NOTE_PREFIX + " | " + metadata
                + " | " + originalNote;
    }

    /**
     * Authoritative MASTER lookup.
     *
     * Existence comes from the F2_FIXED_ASSET side of the LEFT JOIN.
     * Floor / PositionA / PositionAA are MASTER values as stored, never replaced
     * with MAP values. Fac comes from the optional MAP row and may be null when
     * the exact MASTER location has no MAP row (machine still exists).
     *
     * @return null only when F2_FIXED_ASSET has no row for the MachineCode.
     */
    private MasterMachine findMaster(String machineCode) {
        List<FixedAssetMasterMachineDto> rows = repository.findMasterMachinesByCode(machineCode);

        if (rows.isEmpty()) {
            return null;
        }
        // Same logical location already collapsed by the query (GROUP BY).
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "MachineCode has multiple master locations: " + machineCode);
        }

        FixedAssetMasterMachineDto row = rows.get(0);
        boolean locationResolved = hasText(row.getFloor())
                && hasText(row.getPositionA())
                && hasText(row.getPositionAA());

        String fac = hasText(row.getFac()) ? row.getFac().trim() : null;

        return new MasterMachine(
                row.getDiv(),
                row.getMachineCode(),
                fac,
                row.getFloor(),
                row.getPositionA(),
                row.getPositionAA(),
                row.getFaName(),
                locationResolved,
                fac != null
        );
    }

    /**
     * Floor / PositionA / PositionAA are always compared. Fac is compared only
     * when MASTER Fac was resolved reliably, so the core mismatch is still
     * detected when the MASTER mapping is invalid.
     */
    private boolean locationsMatch(MasterMachine master, FixedAssetResolvedLocationDto actual) {
        if (!master.locationResolved()) {
            return false;
        }

        return sameLocationValue(master.fac(), actual.getFac())
                && sameLocationValue(master.floor(), actual.getFloor())
                && sameLocationValue(master.positionA(), actual.getPositionA())
                && sameLocationValue(master.positionAA(), actual.getPositionAA());
    }

    /** "AUTO" / "MANUAL" / "NO_QR" (case-insensitive, trimmed); anything else -> null, never rejected. */
    private String normalizeAuditMode(String mode) {
        if (mode == null) {
            return null;
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        return AUDIT_MODES.contains(normalized) ? normalized : null;
    }

    /** Case-insensitive like the DB collation (SQL_Latin1_General_CP1_CI_AS). */
    private boolean sameZoneValue(String first, String second) {
        return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean sameLocationValue(String first, String second) {
        return Objects.equals(normalizeLocationValue(first), normalizeLocationValue(second));
    }

    private String normalizeLocationValue(String value) {
        return value == null ? null : value.trim();
    }

    /** Unresolved value in a note is written as "?" (never invented). */
    private String noteValue(String value) {
        return hasText(value) ? value.trim() : "?";
    }

    private String mismatchNote(
            MasterMachine master,
            String actualFac,
            String actualFloor,
            String actualPositionA,
            String actualPositionAA,
            String originalNote
    ) {
        // Fixed 4-part format; unresolved MASTER Fac -> "?", e.g. MASTER=?/1F/A99/A35-1
        String masterLocation = String.join("/",
                noteValue(master.fac()),
                noteValue(master.floor()),
                noteValue(master.positionA()),
                noteValue(master.positionAA()));
        String actualLocation = String.join("/",
                actualFac, actualFloor, actualPositionA, actualPositionAA);
        String metadata = "MASTER=" + masterLocation + " | ACTUAL=" + actualLocation;

        if (originalNote == null || originalNote.isBlank()) {
            return LOCATION_MISMATCH_NOTE_PREFIX + " | " + metadata;
        }
        if (originalNote.contains(LOCATION_MISMATCH_NOTE_PREFIX)) {
            String noteWithoutMarker = originalNote
                    .replace(LOCATION_MISMATCH_NOTE_PREFIX, "")
                    .trim();
            return noteWithoutMarker.isEmpty()
                    ? LOCATION_MISMATCH_NOTE_PREFIX + " | " + metadata
                    : LOCATION_MISMATCH_NOTE_PREFIX + " | " + metadata + " | " + noteWithoutMarker;
        }
        return LOCATION_MISMATCH_NOTE_PREFIX + " | " + metadata + " | " + originalNote;
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

    /**
     * One logical MASTER record. fac is null when the exact MASTER location has
     * no MAP row (mappingValid = false); the machine still exists in MASTER.
     */
    private record MasterMachine(
            String div,
            String machineCode,
            String fac,
            String floor,
            String positionA,
            String positionAA,
            String faName,
            boolean locationResolved,
            boolean mappingValid
    ) {
    }

    private record AuditPeriod(
            LocalDate start,
            LocalDate end,
            LocalDateTime startDateTime,
            LocalDateTime nextStartDateTime
    ) {
    }
}
