package com.example.patrol_be.fixedasset.service;

import com.example.patrol_be.fixedasset.dto.FixedAssetAuditCheckRequest;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditCheckResponse;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSaveRequest;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSaveResponse;
import com.example.patrol_be.fixedasset.dto.FixedAssetAuditSummaryDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineLocationDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMasterMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetScanInfoDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetResolvedLocationDto;
import com.example.patrol_be.fixedasset.repository.FixedAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class FixedAssetService {
    private static final String UNKNOWN_MACHINE_NOTE_PREFIX = "NOT_FOUND_IN_MASTER";
    private static final String LOCATION_MISMATCH_NOTE_PREFIX = "LOCATION_MISMATCH";
    private static final String ACTUAL_LOCATION_NOT_MAPPED_NOTE_PREFIX =
            "ACTUAL_LOCATION_NOT_MAPPED";

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
        return repository.findMachines(
                requireValue(fac, "Fac"),
                requireValue(floor, "Floor"),
                requireValue(positionA, "PositionA"),
                requireValue(positionAA, "PositionAA")
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

        LocalDateTime lastAuditedAt = repository.findLatestAuditInPeriod(
                normalizedMachineCode,
                period.startDateTime(),
                period.nextStartDateTime()
        );

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

    @Transactional(readOnly = true)
    public FixedAssetAuditCheckResponse checkAudit(FixedAssetAuditCheckRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String machineCode = requireValue(request.getMachineCode(), "MachineCode");
        String floor = requireValue(request.getFloor(), "Floor");
        String positionAA = requireValue(request.getPositionAA(), "PositionAA");
        AuditPeriod period = currentAuditPeriod();

        LocalDateTime lastAuditedAt = repository.findLatestAuditInPeriod(
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
        boolean alreadyAudited = lastAuditedAt != null;
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
                lastAuditedAt,
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
                message
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
                    false,
                    false,
                    machineCode,
                    null,
                    lastAuditedAt,
                    null,
                    null,
                    null,
                    null,
                    fac,
                    floor,
                    positionA,
                    positionAA,
                    "Machine already audited in current period."
            );
        }

        MasterMachine master = findMaster(machineCode);

        if (master != null) {
            FixedAssetResolvedLocationDto resolvedActual =
                    resolveActualLocation(floor, positionAA);

            if (resolvedActual == null) {
                if (!Boolean.TRUE.equals(request.getConfirmLocationMismatch())) {
                    return new FixedAssetAuditSaveResponse(
                            false,
                            false,
                            false,
                            false,
                            true,
                            true,
                            machineCode,
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
                            "Actual location not found in MAP. Confirmation required."
                    );
                }

                note = unmappedActualNote(master, floor, positionAA, note);
                LocalDateTime updatedAt = LocalDateTime.now();
                int inserted = repository.insertAudit(
                        machineCode,
                        null,
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
                        false,
                        true,
                        false,
                        machineCode,
                        updatedAt,
                        null,
                        master.fac(),
                        master.floor(),
                        master.positionA(),
                        master.positionAA(),
                        null,
                        floor,
                        null,
                        positionAA,
                        "Audit saved with unmapped actual location."
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
                    true,
                    true,
                    machineCode,
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
                    "Machine location does not match master."
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

        LocalDateTime updatedAt = LocalDateTime.now();

        // A_Act / AA_Act = ACTUAL location of this audit. MASTER is never modified.
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
                locationMismatch,
                false,
                machineCode,
                updatedAt,
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
