package com.example.patrol_be.fixedasset.repository;

import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.AUDITED_CONDITION;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.FIRST_AUDIT_IN_PERIOD;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.LATEST_AUDIT_IN_PERIOD;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.MACHINE_PIC;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.NEEDS_QR_REPRINT_CONDITION;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.PIC_EXPR;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.SCOPE_CONDITION;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.effectiveMachineParams;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.effectiveMachineSource;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.picFilter;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.scopeParams;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.toLocalDateTime;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicMachineDto;
import com.example.patrol_be.fixedasset.dto.pic.FixedAssetPicZoneDto;
import com.example.patrol_be.fixedasset.dto.summary.FixedAssetSummaryMachineRowDto;
import com.example.patrol_be.fixedasset.dto.summary.FixedAssetSummaryMonthDto;
import com.example.patrol_be.fixedasset.dto.summary.FixedAssetSummaryPicRowDto;

/** Audit summary counts, PIC list / zones / machines and summary source rows. */
@Repository
public class FixedAssetSummaryRepository {
    private final JdbcTemplate jdbcTemplate;
    private final String div;
    private final String factory;
    private final String mapDivCondition;

    public FixedAssetSummaryRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${fixed-asset.div:KVH}") String div,
            @Value("${fixed-asset.factory:Factory 2}") String factory) {
        this.jdbcTemplate = jdbcTemplate;
        this.div = div.trim();
        this.factory = factory.trim();
        this.mapDivCondition = FixedAssetSql.mapDivCondition(this.div);
    }

    public long countMasterMachines(String pic) {
        String sql = """
                SELECT COUNT(DISTINCT LTRIM(RTRIM(fa.MachineCode)))
                FROM F2Database.dbo.F2_FIXED_ASSET fa
                """
                + "WHERE " + SCOPE_CONDITION + "\n"
                + picFilter(pic)
                + """
                    AND fa.MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(fa.MachineCode)) <> ''
                """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class, scopeParams(div, factory, pic).toArray());
        return count == null ? 0 : count;
    }

    public long countAuditedMasterMachinesInPeriod(
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart,
            String pic) {
        String sql = "SELECT COUNT(DISTINCT latest.MachineCode)\n"
                + "FROM " + LATEST_AUDIT_IN_PERIOD + " latest\n"
                + "WHERE " + AUDITED_CONDITION + "\n"
                + """
                            AND EXISTS (
                                SELECT 1
                                FROM F2Database.dbo.F2_FIXED_ASSET fa
                        """
                + "        WHERE " + SCOPE_CONDITION + "\n"
                + picFilter(pic)
                + """
                                    AND LTRIM(RTRIM(fa.MachineCode)) = latest.MachineCode
                            )
                        """;

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(periodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));
        params.addAll(scopeParams(div, factory, pic));

        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count == null ? 0 : count;
    }

    /**
     * PICs (EmailChecked, normalized; NULL / blank skipped) of the factory.
     * machineCount: all MASTER machines of the PIC (as countMasterMachines);
     * zoneCount: distinct effective Fac / Floor / A / AA of its machines (as
     * findPicZones, see FixedAssetSql.effectiveMachineSource).
     * name is filled by the service.
     */
    public List<FixedAssetPicDto> findPics(
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT
                    p.Pic,
                    p.MachineCount,
                    ISNULL(z.ZoneCount, 0) AS ZoneCount
                FROM (
                    SELECT
                """
                + "        " + PIC_EXPR + " AS Pic,\n"
                + """
                        COUNT(DISTINCT LTRIM(RTRIM(fa.MachineCode))) AS MachineCount
                    FROM F2Database.dbo.F2_FIXED_ASSET fa
                """
                + "    WHERE " + SCOPE_CONDITION + "\n"
                + """
                        AND fa.EmailChecked IS NOT NULL
                        AND LTRIM(RTRIM(fa.EmailChecked)) <> ''
                        AND fa.MachineCode IS NOT NULL
                        AND LTRIM(RTRIM(fa.MachineCode)) <> ''
                """
                + "    GROUP BY " + PIC_EXPR + "\n"
                + """
                ) p
                LEFT JOIN (
                    SELECT
                        eff.Pic,
                        COUNT(DISTINCT CASE WHEN eff.Fac IS NOT NULL THEN
                            eff.Fac + '|' + eff.Floor + '|' + eff.PositionA + '|' + eff.PositionAA
                        END) AS ZoneCount
                    FROM
                """
                + effectiveMachineSource(mapDivCondition, null)
                + """
                    WHERE eff.Pic IS NOT NULL
                        AND eff.Pic <> ''
                    GROUP BY eff.Pic
                ) z
                    ON z.Pic = p.Pic
                ORDER BY p.Pic
                """;

        List<Object> params = scopeParams(div, factory, null);
        params.addAll(effectiveMachineParams(div, factory, null, periodStart, nextPeriodStart));

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetPicDto(
                        resultSet.getString("Pic"),
                        null,
                        resultSet.getLong("MachineCount"),
                        resultSet.getLong("ZoneCount")),
                params.toArray());
    }

    /** Zones of one PIC with progress; same conditions as findZoneProgress (effective location). */
    public List<FixedAssetPicZoneDto> findPicZones(
            String pic,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT
                    eff.Fac,
                    eff.Floor,
                    eff.PositionA,
                    eff.PositionAA,
                    COUNT(DISTINCT eff.MachineCode) AS Total,
                    COUNT(DISTINCT CASE WHEN eff.AuditedInPeriod = 1
                        THEN eff.MachineCode END) AS Audited,
                    COUNT(DISTINCT CASE WHEN eff.Moved = 1
                        THEN eff.MachineCode END) AS MovedCount
                FROM
                """
                + effectiveMachineSource(mapDivCondition, pic)
                + """
                        WHERE eff.Fac IS NOT NULL
                            AND eff.PositionA IS NOT NULL
                            AND eff.PositionA <> ''
                            AND eff.PositionAA IS NOT NULL
                            AND eff.PositionAA <> ''
                        GROUP BY
                            eff.Fac,
                            eff.Floor,
                            eff.PositionA,
                            eff.PositionAA
                        ORDER BY Fac, Floor, PositionA, PositionAA
                        """;

        List<Object> params = effectiveMachineParams(div, factory, pic, periodStart, nextPeriodStart);

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetPicZoneDto(
                        resultSet.getString("Fac"),
                        resultSet.getString("Floor"),
                        resultSet.getString("PositionA"),
                        resultSet.getString("PositionAA"),
                        resultSet.getLong("Total"),
                        resultSet.getLong("Audited"),
                        resultSet.getLong("MovedCount")),
                params.toArray());
    }

    /**
     * Machines of one PIC (summary detail) by effective location (see
     * FixedAssetSql.effectiveMachineSource) with the latest audit in the period. pic null ->
     * machines without PIC (no MASTER row with a non-blank EmailChecked, as the
     * summary). fac / floor / positionA / positionAA: optional filters on the
     * effective location. One row per machine × effective location, MASTER
     * duplicates collapse (moved only when no MASTER row equals the effective
     * location, as findMachines). Not audited first, then MachineCode.
     */
    public List<FixedAssetPicMachineDto> findPicMachines(
            String pic,
            String fac,
            String floor,
            String positionA,
            String positionAA,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        List<Object> params = effectiveMachineParams(div, factory, pic, periodStart, nextPeriodStart);
        if (fac != null) {
            where.append("    AND eff.Fac = ?\n");
            params.add(fac);
        }
        if (floor != null) {
            where.append("    AND eff.Floor = ?\n");
            params.add(floor);
        }
        if (positionA != null) {
            where.append("    AND eff.PositionA = ?\n");
            params.add(positionA);
        }
        if (positionAA != null) {
            where.append("    AND eff.PositionAA = ?\n");
            params.add(positionAA);
        }
        if (pic == null) {
            where.append("    AND NOT EXISTS (\n")
                    .append("        SELECT 1\n")
                    .append("        FROM F2Database.dbo.F2_FIXED_ASSET fa\n")
                    .append("        WHERE ").append(SCOPE_CONDITION).append("\n")
                    .append("            AND LTRIM(RTRIM(fa.MachineCode)) = eff.MachineCode\n")
                    .append("            AND ISNULL(").append(PIC_EXPR).append(", '') <> ''\n")
                    .append("    )\n");
            params.add(div);
            params.add(factory);
        }

        String sql = """
                SELECT
                    eff.MachineCode,
                    MAX(eff.FAName) AS FAName,
                    eff.Fac,
                    eff.Floor,
                    eff.PositionA,
                    eff.PositionAA,
                    MAX(eff.AuditedInPeriod) AS AuditedInPeriod,
                    MAX(eff.AuditUserId) AS AuditUserId,
                    MAX(eff.AuditUserName) AS AuditUserName,
                    MAX(eff.AuditedAt) AS AuditedAt,
                    MAX(eff.AuditMode) AS AuditMode,
                    MAX(eff.AuditQrMismatch) AS AuditQrMismatch,
                    MAX(eff.AuditReprintSts) AS AuditReprintSts,
                    MAX(eff.Fac_Qr) AS Fac_Qr,
                    MAX(eff.Floor_Qr) AS Floor_Qr,
                    MAX(eff.A_Qr) AS A_Qr,
                    MAX(eff.AA_Qr) AS AA_Qr,
                    MAX(eff.AuditNote) AS AuditNote,
                    MIN(CONCAT(eff.MasterFac, '/', eff.MasterFloor, '/',
                        eff.MasterPositionA, '/', eff.MasterPositionAA)) AS MasterLocation,
                    MIN(eff.Moved) AS Moved
                FROM
                """
                + effectiveMachineSource(mapDivCondition, pic)
                + where
                + """
                GROUP BY
                    eff.MachineCode,
                    eff.Fac,
                    eff.Floor,
                    eff.PositionA,
                    eff.PositionAA
                ORDER BY MAX(eff.AuditedInPeriod), eff.MachineCode, eff.Fac, eff.Floor,
                    eff.PositionA, eff.PositionAA
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> {
                    boolean audited = resultSet.getInt("AuditedInPeriod") == 1;
                    return new FixedAssetPicMachineDto(
                            resultSet.getString("MachineCode"),
                            resultSet.getString("FAName"),
                            resultSet.getString("Fac"),
                            resultSet.getString("Floor"),
                            resultSet.getString("PositionA"),
                            resultSet.getString("PositionAA"),
                            audited,
                            auditUserName(
                                    resultSet.getString("AuditUserId"),
                                    resultSet.getString("AuditUserName")),
                            toLocalDateTime(resultSet.getTimestamp("AuditedAt")),
                            resultSet.getString("AuditMode"),
                            resultSet.getInt("AuditQrMismatch") == 1,
                            qrLocation(
                                    resultSet.getString("Fac_Qr"),
                                    resultSet.getString("Floor_Qr"),
                                    resultSet.getString("A_Qr"),
                                    resultSet.getString("AA_Qr")),
                            audited ? resultSet.getInt("AuditReprintSts") == 1 : null,
                            resultSet.getInt("Moved") == 1,
                            resultSet.getString("MasterLocation"),
                            resultSet.getString("AuditNote"));
                },
                params.toArray());
    }

    /** "MSNV_HoTen"; UserName kept as is when it already starts with "MSNV_". */
    private static String auditUserName(String userId, String userName) {
        boolean hasId = userId != null && !userId.isBlank();
        boolean hasName = userName != null && !userName.isBlank();
        if (!hasId) {
            return hasName ? userName : null;
        }
        if (!hasName) {
            return userId;
        }
        return userName.startsWith(userId + "_") ? userName : userId + "_" + userName;
    }

    /** "Fac/Floor/A/AA" of the QR label; null when every level is blank. */
    private static String qrLocation(String fac, String floor, String positionA, String positionAA) {
        if (Stream.of(fac, floor, positionA, positionAA).allMatch(value -> value == null || value.isBlank())) {
            return null;
        }
        return String.join("/",
                Objects.toString(fac, ""),
                Objects.toString(floor, ""),
                Objects.toString(positionA, ""),
                Objects.toString(positionAA, ""));
    }

    /**
     * Summary source in one query: every MASTER machine in scope × its effective
     * location (see FixedAssetSql.effectiveMachineSource; fac null when not audited and its
     * MASTER location is not mapped), with the machine's
     * PIC, first audit time in the period and needsQrReprint of its latest row.
     * AuditedInPeriod uses the same LATEST_AUDIT_IN_PERIOD + AUDITED_CONDITION as
     * countAuditedMasterMachinesInPeriod / findMachines / findZoneProgress, so
     * /summary audited equals /audit-summary for the same period and scope.
     * FirstAuditAt only places the audit on the weekly timeline.
     */
    public List<FixedAssetSummaryMachineRowDto> findSummaryMachines(
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT
                    loc.MachineCode,
                    loc.FAName,
                    mp.Pic,
                    loc.Fac,
                    loc.Floor,
                    loc.PositionA,
                    loc.PositionAA,
                    au.FirstAuditAt,
                    loc.Moved,
                """
                + "    CASE WHEN " + AUDITED_CONDITION + " THEN 1 ELSE 0 END AS AuditedInPeriod,\n"
                + "    CASE WHEN " + NEEDS_QR_REPRINT_CONDITION + " THEN 1 ELSE 0 END AS NeedsQrReprint\n"
                + """
                FROM (
                    SELECT
                        eff.MachineCode,
                        eff.Fac,
                        eff.Floor,
                        eff.PositionA,
                        eff.PositionAA,
                        MAX(eff.FAName) AS FAName,
                        MIN(eff.Moved) AS Moved
                    FROM
                """
                + effectiveMachineSource(mapDivCondition, null)
                + """
                    GROUP BY
                        eff.MachineCode,
                        eff.Fac,
                        eff.Floor,
                        eff.PositionA,
                        eff.PositionAA
                ) loc
                """
                + "INNER JOIN " + MACHINE_PIC + " mp\n"
                + "    ON mp.MachineCode = loc.MachineCode\n"
                + "LEFT JOIN " + FIRST_AUDIT_IN_PERIOD + " au\n"
                + "    ON au.MachineCode = loc.MachineCode\n"
                + "LEFT JOIN " + LATEST_AUDIT_IN_PERIOD + " latest\n"
                + "    ON latest.MachineCode = loc.MachineCode\n"
                + "ORDER BY loc.MachineCode, loc.Fac, loc.Floor, loc.PositionA, loc.PositionAA";

        List<Object> params = effectiveMachineParams(div, factory, null, periodStart, nextPeriodStart);
        params.addAll(scopeParams(div, factory, null));
        params.add(Timestamp.valueOf(periodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));
        params.add(Timestamp.valueOf(periodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetSummaryMachineRowDto(
                        resultSet.getString("MachineCode"),
                        resultSet.getString("FAName"),
                        resultSet.getString("Pic"),
                        resultSet.getString("Fac"),
                        resultSet.getString("Floor"),
                        resultSet.getString("PositionA"),
                        resultSet.getString("PositionAA"),
                        toLocalDateTime(resultSet.getTimestamp("FirstAuditAt")),
                        resultSet.getInt("AuditedInPeriod") == 1,
                        resultSet.getInt("NeedsQrReprint") == 1,
                        resultSet.getInt("Moved") == 1),
                params.toArray());
    }

    /**
     * Quarter progress per current PIC (MACHINE_PIC; pic null = machines without
     * PIC), counted per machine. total: MASTER machines in scope (same set as
     * findSummaryMachines). audited: same LATEST_AUDIT_IN_PERIOD + AUDITED_CONDITION
     * as findSummaryMachines. months: machines whose first audit in the period
     * falls in each of the 3 months. isTotal = false on every row.
     */
    public List<FixedAssetSummaryPicRowDto> findPicMonthlyProgress(
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT
                    mp.Pic,
                    COUNT(*) AS Total,
                """
                + "    SUM(CASE WHEN " + AUDITED_CONDITION + " THEN 1 ELSE 0 END) AS Audited,\n"
                + """
                    SUM(CASE WHEN au.FirstAuditAt < ? THEN 1 ELSE 0 END) AS Month1,
                    SUM(CASE WHEN au.FirstAuditAt >= ? AND au.FirstAuditAt < ? THEN 1 ELSE 0 END) AS Month2,
                    SUM(CASE WHEN au.FirstAuditAt >= ? THEN 1 ELSE 0 END) AS Month3
                """
                + "FROM " + MACHINE_PIC + " mp\n"
                + "LEFT JOIN " + FIRST_AUDIT_IN_PERIOD + " au\n"
                + "    ON au.MachineCode = mp.MachineCode\n"
                + "LEFT JOIN " + LATEST_AUDIT_IN_PERIOD + " latest\n"
                + "    ON latest.MachineCode = mp.MachineCode\n"
                + "GROUP BY mp.Pic\n"
                + "ORDER BY mp.Pic";

        LocalDateTime month2Start = periodStart.plusMonths(1);
        LocalDateTime month3Start = periodStart.plusMonths(2);
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(month2Start));
        params.add(Timestamp.valueOf(month2Start));
        params.add(Timestamp.valueOf(month3Start));
        params.add(Timestamp.valueOf(month3Start));
        params.addAll(scopeParams(div, factory, null));
        params.add(Timestamp.valueOf(periodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));
        params.add(Timestamp.valueOf(periodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetSummaryPicRowDto(
                        resultSet.getString("Pic"),
                        false,
                        resultSet.getLong("Total"),
                        resultSet.getLong("Audited"),
                        List.of(
                                new FixedAssetSummaryMonthDto(
                                        periodStart.getMonthValue(), resultSet.getLong("Month1")),
                                new FixedAssetSummaryMonthDto(
                                        month2Start.getMonthValue(), resultSet.getLong("Month2")),
                                new FixedAssetSummaryMonthDto(
                                        month3Start.getMonthValue(), resultSet.getLong("Month3")))),
                params.toArray());
    }
}
