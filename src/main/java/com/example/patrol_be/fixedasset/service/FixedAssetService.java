package com.example.patrol_be.fixedasset.service;

import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditCheckRequest;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditCheckResponse;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveRequest;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSaveResponse;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditSummaryDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMachineDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMachineLocationDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMasterMachineDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicDetailDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicMachineDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicSummaryDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicZoneDto;
import com.example.patrol_be.fixedasset.dto.summary.FixedAssetSummaryDto;
import com.example.patrol_be.fixedasset.dto.summary.FixedAssetSummaryMachineRowDto;
import com.example.patrol_be.fixedasset.dto.summary.FixedAssetSummaryMonthDto;
import com.example.patrol_be.fixedasset.dto.summary.FixedAssetSummaryPicRowDto;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetUnauditedMachineDto;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetLatestAuditDto;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetLatestAutoAuditDto;
import com.example.patrol_be.fixedasset.dto.scan.FixedAssetQrMismatchDto;
import com.example.patrol_be.fixedasset.dto.zone.FixedAssetZoneLockDto;
import com.example.patrol_be.fixedasset.dto.scan.FixedAssetScanInfoDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetResolvedLocationDto;
import com.example.patrol_be.fixedasset.dto.zone.FixedAssetZoneProgressDto;
import com.example.patrol_be.fixedasset.repository.FixedAssetAuditRepository;
import com.example.patrol_be.fixedasset.repository.FixedAssetLocationRepository;
import com.example.patrol_be.fixedasset.repository.FixedAssetSummaryRepository;
import com.example.patrol_be.common.exception.PermissionDeniedException;
import com.example.patrol_be.hr.repository.HrDataRepository;
import com.example.patrol_be.permission.PatrolAction;
import com.example.patrol_be.permission.PatrolGroup;
import com.example.patrol_be.permission.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FixedAssetService {
    private static final String UNKNOWN_MACHINE_NOTE_PREFIX = "NOT_FOUND_IN_MASTER";
    private static final String LOCATION_MISMATCH_NOTE_PREFIX = "LOCATION_MISMATCH";
    private static final String ACTUAL_LOCATION_NOT_MAPPED_NOTE_PREFIX =
            "ACTUAL_LOCATION_NOT_MAPPED";
    private static final String NO_QR_MODE = "NO_QR";
    private static final String NO_QR_NOTE_PREFIX = "NO_QR";
    private static final String PIC_MISMATCH_NOTE_PREFIX = "PIC_MISMATCH";
    private static final Set<String> AUDIT_MODES = Set.of("AUTO", "MANUAL", NO_QR_MODE);
    private static final String ZONE_LOCK_INCOMPLETE = "INCOMPLETE";
    private static final String ZONE_LOCK_COMPLETED = "COMPLETED";
    private static final String ZONE_LOCK_NO_AUTO_AUDIT = "NO_AUTO_AUDIT";
    private static final String ZONE_LOCK_UNRESOLVED = "UNRESOLVED";
    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("^(\\d{4})-Q([1-4])$", Pattern.CASE_INSENSITIVE);
    private static final String STATUS_NOT_STARTED = "NOT_STARTED";
    private static final String STATUS_ON_TRACK = "ON_TRACK";
    private static final String STATUS_BEHIND = "BEHIND";
    private static final int MONTHS_PER_PERIOD = 3;
    private static final String NO_PIC_NAME = "Chưa có PIC";
    private static final int SPEED_WINDOW_DAYS = 28;

    private final FixedAssetLocationRepository locationRepository;
    private final FixedAssetAuditRepository auditRepository;
    private final FixedAssetSummaryRepository summaryRepository;
    private final HrDataRepository hrDataRepository;
    private final PermissionService permissionService;

    public FixedAssetService(
            FixedAssetLocationRepository locationRepository,
            FixedAssetAuditRepository auditRepository,
            FixedAssetSummaryRepository summaryRepository,
            HrDataRepository hrDataRepository,
            PermissionService permissionService) {
        this.locationRepository = locationRepository;
        this.auditRepository = auditRepository;
        this.summaryRepository = summaryRepository;
        this.hrDataRepository = hrDataRepository;
        this.permissionService = permissionService;
    }

    public List<String> getFacs(String pic) {
        AuditPeriod period = currentAuditPeriod();
        return locationRepository.findFacs(normalizePic(pic), period.startDateTime(), period.nextStartDateTime());
    }

    public List<String> getFloors(String fac, String pic) {
        AuditPeriod period = currentAuditPeriod();
        return locationRepository.findFloors(
                requireValue(fac, "Fac"),
                normalizePic(pic),
                period.startDateTime(),
                period.nextStartDateTime()
        );
    }

    public List<String> getPositionA(String fac, String floor, String pic) {
        AuditPeriod period = currentAuditPeriod();
        return locationRepository.findPositionA(
                requireValue(fac, "Fac"),
                requireValue(floor, "Floor"),
                normalizePic(pic),
                period.startDateTime(),
                period.nextStartDateTime()
        );
    }

    public List<String> getPositionAA(String fac, String floor, String positionA, String pic) {
        AuditPeriod period = currentAuditPeriod();
        return locationRepository.findPositionAA(
                requireValue(fac, "Fac"),
                requireValue(floor, "Floor"),
                requireValue(positionA, "PositionA"),
                normalizePic(pic),
                period.startDateTime(),
                period.nextStartDateTime()
        );
    }

    public List<FixedAssetMachineDto> getMachines(
            String fac,
            String floor,
            String positionA,
            String positionAA,
            String pic
    ) {
        String normalizedFac = requireValue(fac, "Fac");
        String normalizedFloor = requireValue(floor, "Floor");
        String normalizedPositionA = requireValue(positionA, "PositionA");
        String normalizedPositionAA = requireValue(positionAA, "PositionAA");
        AuditPeriod period = currentAuditPeriod();

        return locationRepository.findMachines(
                normalizedFac,
                normalizedFloor,
                normalizedPositionA,
                normalizedPositionAA,
                normalizePic(pic),
                period.startDateTime(),
                period.nextStartDateTime()
        );
    }

    /** PICs of the factory; name = part of the email before '@' (HSE_EmpID has no email). */
    public List<FixedAssetPicDto> getPics() {
        AuditPeriod period = currentAuditPeriod();
        List<FixedAssetPicDto> pics = summaryRepository.findPics(period.startDateTime(), period.nextStartDateTime());
        for (FixedAssetPicDto pic : pics) {
            String email = pic.getEmail();
            int at = email.indexOf('@');
            pic.setName(at > 0 ? email.substring(0, at) : email);
        }
        return pics;
    }

    public List<FixedAssetPicZoneDto> getPicZones(String pic) {
        String normalizedPic = normalizePic(requireValue(pic, "Pic"));
        AuditPeriod period = currentAuditPeriod();
        return summaryRepository.findPicZones(
                normalizedPic,
                period.startDateTime(),
                period.nextStartDateTime()
        );
    }

    // ===============================
    // SUMMARY per period / PIC (one query, aggregated here)
    // ===============================

    public FixedAssetSummaryDto getSummary(String period) {
        AuditPeriod auditPeriod = auditPeriod(period);
        LocalDate today = LocalDate.now();
        double planPct = planPctAt(auditPeriod, today);
        List<MachineStat> machines = summaryMachines(auditPeriod);

        long total = machines.size();
        long audited = machines.stream().filter(MachineStat::audited).count();
        long remaining = total - audited;
        long pendingQr = machines.stream().filter(MachineStat::needsQrReprint).count();

        // "Chưa có PIC" (pic = null) is not counted as a PIC.
        List<FixedAssetPicSummaryDto> pics = picSummaries(machines, auditPeriod, today, planPct)
                .stream()
                .filter(pic -> pic.getPic() != null)
                .toList();

        return new FixedAssetSummaryDto(
                auditPeriod.start(),
                auditPeriod.end(),
                today,
                planPct,
                total,
                audited,
                percent(audited, total),
                remaining,
                pendingQr,
                pics.stream().filter(pic -> pic.getPendingQr() > 0).count(),
                pics.size(),
                pics.stream()
                        .filter(pic -> STATUS_ON_TRACK.equals(pic.getStatus()))
                        .count(),
                summaryPicRows(auditPeriod)
        );
    }

    /** Sorted by pct ascending; machines without PIC grouped as pic = null. */
    public List<FixedAssetPicSummaryDto> getSummaryPics(String period) {
        AuditPeriod auditPeriod = auditPeriod(period);
        LocalDate today = LocalDate.now();
        return picSummaries(
                summaryMachines(auditPeriod), auditPeriod, today, planPctAt(auditPeriod, today));
    }

    /** pic blank -> machines without PIC ("Chưa có PIC"). */
    public FixedAssetPicDetailDto getSummaryPicDetail(String pic, String period) {
        String normalizedPic = normalizePic(pic);
        AuditPeriod auditPeriod = auditPeriod(period);
        LocalDate today = LocalDate.now();
        List<MachineStat> machines = summaryMachines(auditPeriod).stream()
                .filter(machine -> Objects.equals(machine.pic(), normalizedPic))
                .toList();
        if (machines.isEmpty()) {
            throw new IllegalArgumentException("No machine for PIC: "
                    + (normalizedPic == null ? NO_PIC_NAME : normalizedPic));
        }

        FixedAssetPicSummaryDto summary = picSummary(
                normalizedPic, machines, auditPeriod, today, planPctAt(auditPeriod, today));

        // Zone -> distinct machines (total / audited), mapped locations only.
        Map<String, FixedAssetPicZoneDto> zones = new LinkedHashMap<>();
        Map<String, Set<String>> zoneMachines = new LinkedHashMap<>();
        Map<String, Set<String>> zoneAudited = new LinkedHashMap<>();
        Map<String, Set<String>> zoneMoved = new LinkedHashMap<>();
        List<FixedAssetUnauditedMachineDto> unaudited = new ArrayList<>();
        for (MachineStat machine : machines) {
            for (FixedAssetSummaryMachineRowDto row : machine.rows()) {
                if (!machine.audited()) {
                    unaudited.add(new FixedAssetUnauditedMachineDto(
                            row.getMachineCode(),
                            row.getFaName(),
                            row.getFac(),
                            row.getFloor(),
                            row.getPositionA(),
                            row.getPositionAA()));
                }
                String key = zoneKey(row);
                if (key == null) {
                    continue;
                }
                zones.computeIfAbsent(key, k -> new FixedAssetPicZoneDto(
                        row.getFac(), row.getFloor(), row.getPositionA(), row.getPositionAA(), 0, 0, 0));
                zoneMachines.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(machine.machineCode());
                if (machine.audited()) {
                    zoneAudited.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(machine.machineCode());
                }
                if (row.isMoved()) {
                    zoneMoved.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(machine.machineCode());
                }
            }
        }
        zones.forEach((key, zone) -> {
            zone.setTotal(zoneMachines.get(key).size());
            zone.setAudited(zoneAudited.getOrDefault(key, Set.of()).size());
            zone.setMovedCount(zoneMoved.getOrDefault(key, Set.of()).size());
        });
        List<FixedAssetPicZoneDto> zoneList = zones.values().stream()
                .sorted(Comparator.comparing(FixedAssetPicZoneDto::getFac)
                        .thenComparing(FixedAssetPicZoneDto::getFloor)
                        .thenComparing(FixedAssetPicZoneDto::getPositionA)
                        .thenComparing(FixedAssetPicZoneDto::getPositionAA))
                .toList();

        return new FixedAssetPicDetailDto(
                summary.getPic(),
                summary.getName(),
                summary.getTotal(),
                summary.getAudited(),
                summary.getPct(),
                summary.getRemaining(),
                summary.getZoneCount(),
                summary.getPendingQr(),
                summary.getSpeedPerWeek(),
                summary.getEtaDate(),
                summary.getStatus(),
                zoneList,
                unaudited
        );
    }

    /**
     * Machines of a PIC for the summary detail (pic blank -> machines without
     * PIC); fac / floor / positionA / positionAA blank -> no filter on that level.
     */
    public List<FixedAssetPicMachineDto> getSummaryPicMachines(
            String pic,
            String period,
            String fac,
            String floor,
            String positionA,
            String positionAA) {
        AuditPeriod auditPeriod = auditPeriod(period);
        return summaryRepository.findPicMachines(
                normalizePic(pic),
                trimToNull(fac),
                trimToNull(floor),
                trimToNull(positionA),
                trimToNull(positionAA),
                auditPeriod.startDateTime(),
                auditPeriod.nextStartDateTime()
        );
    }

    /** Rows of the summary query grouped per machine (a machine may have several locations). */
    private List<MachineStat> summaryMachines(AuditPeriod auditPeriod) {
        Map<String, List<FixedAssetSummaryMachineRowDto>> byMachine = new LinkedHashMap<>();
        for (FixedAssetSummaryMachineRowDto row : summaryRepository.findSummaryMachines(
                auditPeriod.startDateTime(), auditPeriod.nextStartDateTime())) {
            byMachine.computeIfAbsent(row.getMachineCode(), k -> new ArrayList<>()).add(row);
        }

        List<MachineStat> machines = new ArrayList<>(byMachine.size());
        byMachine.forEach((machineCode, rows) -> {
            FixedAssetSummaryMachineRowDto first = rows.get(0);
            machines.add(new MachineStat(
                    machineCode,
                    first.getPic(),
                    first.getFirstAuditAt(),
                    first.isAuditedInPeriod(),
                    first.isNeedsQrReprint(),
                    rows));
        });
        return machines;
    }

    private List<FixedAssetPicSummaryDto> picSummaries(
            List<MachineStat> machines,
            AuditPeriod auditPeriod,
            LocalDate today,
            double planPct) {
        // LinkedHashMap accepts the null key (machines without PIC).
        Map<String, List<MachineStat>> byPic = new LinkedHashMap<>();
        for (MachineStat machine : machines) {
            byPic.computeIfAbsent(machine.pic(), k -> new ArrayList<>()).add(machine);
        }

        List<FixedAssetPicSummaryDto> result = new ArrayList<>(byPic.size());
        byPic.forEach((pic, picMachines) ->
                result.add(picSummary(pic, picMachines, auditPeriod, today, planPct)));
        result.sort(Comparator.comparingDouble(FixedAssetPicSummaryDto::getPct)
                .thenComparing(FixedAssetPicSummaryDto::getPic,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private FixedAssetPicSummaryDto picSummary(
            String pic,
            List<MachineStat> machines,
            AuditPeriod auditPeriod,
            LocalDate today,
            double planPct) {
        long total = machines.size();
        long audited = machines.stream().filter(MachineStat::audited).count();
        long remaining = total - audited;
        double pct = percent(audited, total);
        long zoneCount = machines.stream()
                .flatMap(machine -> machine.rows().stream())
                .map(this::zoneKey)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long pendingQr = machines.stream().filter(MachineStat::needsQrReprint).count();
        String status = summaryStatus(audited, pct, planPct);

        // Speed: audits over the last 4 weeks of the period up to now; at least 1 week.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.isBefore(auditPeriod.nextStartDateTime())
                ? now : auditPeriod.nextStartDateTime();
        LocalDateTime windowStart = windowEnd.minusDays(SPEED_WINDOW_DAYS);
        if (windowStart.isBefore(auditPeriod.startDateTime())) {
            windowStart = auditPeriod.startDateTime();
        }
        double speedPerWeek = 0.0;
        if (windowEnd.isAfter(windowStart)) {
            LocalDateTime from = windowStart;
            long recent = machines.stream()
                    .map(MachineStat::firstAuditAt)
                    .filter(at -> at != null && !at.isBefore(from) && at.isBefore(windowEnd))
                    .count();
            double weeks = Math.max(Duration.between(windowStart, windowEnd).toMinutes() / (7 * 24 * 60.0), 1.0);
            speedPerWeek = round2(recent / weeks);
        }

        LocalDate etaDate = remaining <= 0 || speedPerWeek <= 0
                ? null
                : today.plusDays((long) Math.ceil(remaining / speedPerWeek * 7));

        return new FixedAssetPicSummaryDto(
                pic,
                picName(pic),
                total,
                audited,
                pct,
                remaining,
                zoneCount,
                pendingQr,
                speedPerWeek,
                etaDate,
                status
        );
    }

    /**
     * Quarter progress per PIC: total row first (pic = null, isTotal, every
     * machine incl. those without PIC), then one row per PIC, then machines
     * without PIC (pic = null, isTotal = false) when there are any.
     */
    private List<FixedAssetSummaryPicRowDto> summaryPicRows(AuditPeriod auditPeriod) {
        List<FixedAssetSummaryPicRowDto> rows = summaryRepository.findPicMonthlyProgress(
                auditPeriod.startDateTime(), auditPeriod.nextStartDateTime());

        long total = 0;
        long audited = 0;
        long[] monthAudited = new long[MONTHS_PER_PERIOD];
        for (FixedAssetSummaryPicRowDto row : rows) {
            total += row.getTotal();
            audited += row.getAudited();
            for (int i = 0; i < MONTHS_PER_PERIOD; i++) {
                monthAudited[i] += row.getMonths().get(i).getAudited();
            }
        }
        List<FixedAssetSummaryMonthDto> totalMonths = new ArrayList<>(MONTHS_PER_PERIOD);
        for (int i = 0; i < MONTHS_PER_PERIOD; i++) {
            totalMonths.add(new FixedAssetSummaryMonthDto(
                    auditPeriod.start().plusMonths(i).getMonthValue(), monthAudited[i]));
        }

        List<FixedAssetSummaryPicRowDto> picRows = new ArrayList<>(rows.size() + 1);
        picRows.add(new FixedAssetSummaryPicRowDto(null, true, total, audited, totalMonths));
        rows.stream().filter(row -> row.getPic() != null).forEach(picRows::add);
        // Machines without PIC: own row (pic = null, isTotal = false) last.
        rows.stream()
                .filter(row -> row.getPic() == null && row.getTotal() > 0)
                .forEach(picRows::add);
        return picRows;
    }

    /**
     * Monthly target: end of month k = k / 3 × 100; within month k = target of
     * month k - 1 + days passed (through date, inclusive) / days of the month
     * × 1/3 × 100. Before the period = 0, ended period = 100.
     */
    private double planPctAt(AuditPeriod auditPeriod, LocalDate date) {
        if (date.isBefore(auditPeriod.start())) {
            return 0.0;
        }
        if (!date.isBefore(auditPeriod.end())) {
            return 100.0;
        }
        long monthIndex = ChronoUnit.MONTHS.between(
                auditPeriod.start().withDayOfMonth(1), date.withDayOfMonth(1));
        double monthShare = 100.0 / MONTHS_PER_PERIOD;
        return round2(monthIndex * monthShare
                + date.getDayOfMonth() / (double) date.lengthOfMonth() * monthShare);
    }

    private String summaryStatus(long audited, double pct, double planPct) {
        if (audited == 0) {
            return STATUS_NOT_STARTED;
        }
        return pct < planPct ? STATUS_BEHIND : STATUS_ON_TRACK;
    }

    /** Same naming as getPics: part of the email before '@'. */
    private String picName(String pic) {
        if (pic == null) {
            return NO_PIC_NAME;
        }
        int at = pic.indexOf('@');
        return at > 0 ? pic.substring(0, at) : pic;
    }

    /** Fac|Floor|A|AA of a mapped row; null when the location has no MAP row. */
    private String zoneKey(FixedAssetSummaryMachineRowDto row) {
        return row.getFac() == null
                ? null
                : String.join("|", row.getFac(), row.getFloor(), row.getPositionA(), row.getPositionAA());
    }

    private double percent(long part, long total) {
        return total == 0 ? 0.0 : round2(part * 100.0 / total);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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

        FixedAssetLatestAuditDto latestAudit = auditRepository.findLatestAuditInPeriod(
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
                reprint == null ? null : reprint.getQrPositionAA(),
                latestAudit != null && latestAudit.isReprintSts(),
                latestAudit == null ? null : latestAudit.getReprintAt(),
                master == null ? null : master.pic()
        );
    }

    public FixedAssetAuditSummaryDto getAuditSummary(String pic) {
        String normalizedPic = normalizePic(pic);
        AuditPeriod period = currentAuditPeriod();
        long totalMachines = summaryRepository.countMasterMachines(normalizedPic);
        long auditedMachines = summaryRepository.countAuditedMasterMachinesInPeriod(
                period.startDateTime(),
                period.nextStartDateTime(),
                normalizedPic
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

    public List<FixedAssetZoneProgressDto> getZoneProgress(String fac, String floor, String pic) {
        AuditPeriod period = currentAuditPeriod();
        return locationRepository.findZoneProgress(
                requireValue(fac, "Fac"),
                requireValue(floor, "Floor"),
                normalizePic(pic),
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

        FixedAssetLatestAutoAuditDto latestAuto = locationRepository.findLatestAutoAuditInPeriod(
                normalizedUserId,
                period.startDateTime(),
                period.nextStartDateTime()
        );
        if (latestAuto == null) {
            return new FixedAssetZoneLockDto(
                    false, ZONE_LOCK_NO_AUTO_AUDIT, null, null, null, null, null, null, null);
        }

        List<FixedAssetResolvedLocationDto> locations = locationRepository.findLocationsByPositionAAndAA(
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
        FixedAssetZoneProgressDto progress = locationRepository.findZoneProgress(
                        location.getFac(),
                        location.getFloor(),
                        null,
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

        FixedAssetLatestAuditDto latestAudit = auditRepository.findLatestAuditInPeriod(
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
                reprint == null ? null : reprint.getQrPositionAA(),
                latestAudit != null && latestAudit.isReprintSts(),
                latestAudit == null ? null : latestAudit.getReprintAt(),
                existsInMaster ? master.pic() : null
        );
    }

    @Transactional
    public FixedAssetAuditSaveResponse saveAudit(FixedAssetAuditSaveRequest request) {
        return saveAudit(request, null);
    }

    /**
     * photo3: file already written by the "3) Thay QR" scan (null = no photo).
     * It is set as MASTER Photo3 in this transaction only when the rescan writes
     * the audit row (clear / relocate); response photoSaved tells the caller.
     */
    @Transactional
    public FixedAssetAuditSaveResponse saveAudit(FixedAssetAuditSaveRequest request, Photo3Update photo3) {
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
        if (Boolean.TRUE.equals(request.getQrReplace())) {
            requireQrReplacePermission(userId);
        }
        String note = request.getNote() == null ? null : request.getNote().trim();
        String auditMode = normalizeAuditMode(request.getMode());
        // NO_QR: machine has no QR label (form "Ghi nhận máy thiếu QR").
        boolean noQr = NO_QR_MODE.equals(auditMode);
        // Same normalization as EmailChecked; null when not sent.
        String requestPic = normalizePic(request.getPic());
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
        FixedAssetLatestAuditDto latestAudit = auditRepository.findLatestAuditInPeriodForUpdate(
                machineCode,
                period.startDateTime(),
                period.nextStartDateTime()
        );

        if (isAudited(latestAudit)) {
            // Flagged machine outside "3) Thay QR" (incl. NO_QR rows / NO_QR
            // mode): nothing is written, FE is pointed to the replace flow.
            if (latestAudit.isNeedsQrReprint() && !Boolean.TRUE.equals(request.getQrReplace())) {
                return pendingQrReplace(latestAudit, machineCode);
            }

            // Rescan of a flagged machine: decided by the scanned QR label
            // (still under the UPDLOCK above), no new row. NO_QR keeps the
            // mark branch below.
            if (latestAudit.isNeedsQrReprint() && !noQr) {
                return rescanQrReprint(
                        latestAudit,
                        machineCode,
                        userId,
                        fac,
                        floor,
                        positionA,
                        positionAA,
                        qrFac,
                        qrFloor,
                        qrPositionA,
                        qrPositionAA,
                        Boolean.TRUE.equals(request.getConfirmLocationMismatch()),
                        photo3
                );
            }

            // QR mismatch on an already audited machine: flag the existing row
            // for reprint instead of inserting a duplicate.
            if (qrMismatch) {
                int updated = auditRepository.markAuditQrReprint(
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
                            ? qrPositionAA : latestAudit.getQrPositionAA(),
                    false,
                    // mark resets the replacement status
                    !qrMismatch && latestAudit.isReprintSts(),
                    qrMismatch ? null : latestAudit.getReprintAt()
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
                            null,
                            false,
                            false,
                            null
                    );
                }

                note = unmappedActualNote(master, floor, positionAA, note);
                note = picMismatchNote(master, requestPic, note);
                if (noQr) {
                    note = noQrNote(note);
                }
                LocalDateTime updatedAt = LocalDateTime.now();
                int inserted = auditRepository.insertAudit(
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
                        qrMismatch,
                        master.pic()
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
                        qrMismatch ? qrPositionAA : null,
                        false,
                        false,
                        null
                );
            }
        }

        fac = requireValue(fac, "Fac");
        positionA = requireValue(positionA, "PositionA");
        if (!locationRepository.existsLocation(fac, floor, positionA, positionAA)) {
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
                    null,
                    false,
                    false,
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
        if (!unknownMachine) {
            note = picMismatchNote(master, requestPic, note);
        }
        if (noQr) {
            note = noQrNote(note);
        }

        LocalDateTime updatedAt = LocalDateTime.now();

        // *_Act = ACTUAL location of this audit. MASTER is never modified.
        int inserted = auditRepository.insertAudit(
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
                qrMismatch,
                // Machine not in MASTER: PIC sent by the form.
                unknownMachine ? requestPic : master.pic()
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
                qrMismatch ? qrPositionAA : null,
                false,
                false,
                null
        );
    }

    public List<String> getAuditedMachineCodes() {
        return auditRepository.findAuditedMachineCodes();
    }

    public List<FixedAssetQrMismatchDto> getQrMismatchAudits(String fac) {
        AuditPeriod period = currentAuditPeriod();
        return auditRepository.findQrMismatchAudits(
                trimToNull(fac),
                period.startDateTime(),
                period.nextStartDateTime()
        );
    }

    /**
     * "3) Thay QR" = FixedAsset.AFTER (column CanAfter). Role from HSE_EmpID by
     * userId; unknown employee -> denied.
     */
    private void requireQrReplacePermission(String userId) {
        String role = hrDataRepository.findByCode(userId)
                .orElseThrow(() -> new PermissionDeniedException(
                        "Employee not found: " + userId))
                .getRole();
        if (!permissionService.can(role, PatrolGroup.FixedAsset, PatrolAction.AFTER)) {
            throw new PermissionDeniedException(
                    "User " + userId + " has no permission for QR replace (FixedAsset.after).");
        }
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

    /** Same normalization as EmailChecked in SQL (trim + lower); blank -> null (no filter). */
    private String normalizePic(String pic) {
        String trimmed = trimToNull(pic);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
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
                locationRepository.findLocationsByFloorAndPositionAA(floor, positionAA);

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
                locationRepository.findLocationsByFloorAndPositionAA(floor, positionAA).stream()
                        .filter(location -> !hasText(positionA)
                                || positionA.equals(location.getPositionA()))
                        .toList();

        return locations.size() == 1 ? locations.get(0) : null;
    }

    /**
     * Rescan of a machine whose latest row has QrMismatch = 1, decided by the
     * scanned QR label location (qr*):
     * - label = old *_Qr -> label not reprinted yet, nothing changes;
     * - label = *_Act and selected = *_Act (NO_QR row: label = *_Act) ->
     *   ReprintSts = 1; QrMismatch / *_Qr (audit-time label) are kept;
     * - otherwise the machine may have moved -> confirmation, then *_Act and
     *   *_Qr are overwritten, QrMismatch stays 1 and ReprintSts stays 0.
     * photo3 (optional) becomes MASTER Photo3 in the two writing branches.
     */
    private FixedAssetAuditSaveResponse rescanQrReprint(
            FixedAssetLatestAuditDto latest,
            String machineCode,
            String userId,
            String fac,
            String floor,
            String positionA,
            String positionAA,
            String qrFac,
            String qrFloor,
            String qrPositionA,
            String qrPositionAA,
            boolean confirmed,
            Photo3Update photo3) {
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
                    latest.getQrPositionAA(),
                    false,
                    false,
                    null
            );
        }

        if (labelIsActual && (noQrRow || selectedIsActual)) {
            LocalDateTime reprintAt = LocalDateTime.now();
            int updated = auditRepository.clearAuditQrReprint(
                    latest.getId(),
                    reprintAt,
                    userId
            );
            if (updated != 1) {
                throw new IllegalStateException("Fixed Asset QR reprint flag was not cleared");
            }

            return applyPhoto3(new FixedAssetAuditSaveResponse(
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
                    // *_Qr unchanged (audit-time label kept as history)
                    latest.getQrFac(),
                    latest.getQrFloor(),
                    latest.getQrPositionA(),
                    latest.getQrPositionAA(),
                    false,
                    true,
                    reprintAt
            ), machineCode, photo3, userId);
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
                    latest.getQrPositionAA(),
                    false,
                    false,
                    null
            );
        }

        fac = requireValue(fac, "Fac");
        positionA = requireValue(positionA, "PositionA");
        if (!locationRepository.existsLocation(fac, floor, positionA, positionAA)) {
            throw new IllegalArgumentException(
                    "Invalid location: " + fac + " / " + floor + " / " + positionA + " / " + positionAA);
        }

        int updated = auditRepository.relocateAuditQrReprint(
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

        return applyPhoto3(new FixedAssetAuditSaveResponse(
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
                qrPositionAA,
                false,
                false,
                null
        ), machineCode, photo3, userId);
    }

    /** Sets MASTER Photo3 / Photo3_Old in the current transaction. */
    private FixedAssetAuditSaveResponse applyPhoto3(
            FixedAssetAuditSaveResponse response,
            String machineCode,
            Photo3Update photo3,
            String userId) {
        if (photo3 == null) {
            return response;
        }
        if (auditRepository.updateMasterPhoto3(
                machineCode, photo3.photo3(), photo3.photo3Old(), userId) < 1) {
            throw new IllegalStateException("Fixed Asset Photo3 was not saved: " + machineCode);
        }
        response.setPhotoSaved(true);
        response.setPhoto3(photo3.photo3());
        return response;
    }

    /**
     * New MASTER Photo3 file name; photo3Old = renamed old file of the same name,
     * null -> Photo3_Old takes the current Photo3 (when not blank).
     */
    public record Photo3Update(String photo3, String photo3Old) {
    }

    /** Flagged machine scanned without qrReplace: row unchanged, reprint info returned. */
    private FixedAssetAuditSaveResponse pendingQrReplace(
            FixedAssetLatestAuditDto latest,
            String machineCode) {
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
                "Máy đang chờ thay QR – thực hiện ở mục 3) Thay QR",
                false,
                latest.getQrFac(),
                latest.getQrFloor(),
                latest.getQrPositionA(),
                latest.getQrPositionAA(),
                true,
                false,
                null
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
    /**
     * PIC sent differs from the MASTER PIC: MASTER PIC is stored, the sent one
     * is kept in the note ("PIC_MISMATCH | REQ={pic}"). Not sent -> unchanged.
     */
    private String picMismatchNote(MasterMachine master, String requestPic, String note) {
        if (requestPic == null || requestPic.equals(master.pic())) {
            return note;
        }
        String marker = PIC_MISMATCH_NOTE_PREFIX + " | REQ=" + requestPic;
        return note == null || note.isBlank() ? marker : marker + " | " + note;
    }

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
        List<FixedAssetMasterMachineDto> rows = auditRepository.findMasterMachinesByCode(machineCode);

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
                row.getPic(),
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
        return quarterPeriod(today.getYear(), periodStartMonth);
    }

    /** "YYYY-Qn" (n = 1..4, case-insensitive); blank -> current quarter. */
    private AuditPeriod auditPeriod(String period) {
        if (!hasText(period)) {
            return currentAuditPeriod();
        }
        Matcher matcher = PERIOD_PATTERN.matcher(period.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid period (YYYY-Qn): " + period);
        }
        int quarter = Integer.parseInt(matcher.group(2));
        return quarterPeriod(Integer.parseInt(matcher.group(1)), (quarter - 1) * 3 + 1);
    }

    private AuditPeriod quarterPeriod(int year, int startMonth) {
        LocalDate start = LocalDate.of(year, startMonth, 1);
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
            String pic,
            boolean locationResolved,
            boolean mappingValid
    ) {
    }

    /** One summary machine: audited = has an audit row in the period. */
    private record MachineStat(
            String machineCode,
            String pic,
            LocalDateTime firstAuditAt,
            /* Same rule as countAuditedMasterMachinesInPeriod (latest audit in period). */
            boolean audited,
            boolean needsQrReprint,
            List<FixedAssetSummaryMachineRowDto> rows
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
