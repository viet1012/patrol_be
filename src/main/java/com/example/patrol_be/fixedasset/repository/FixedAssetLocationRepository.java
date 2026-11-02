package com.example.patrol_be.fixedasset.repository;

import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.SCOPE_CONDITION;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.effectiveMachineParams;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.effectiveMachineSource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.patrol_be.fixedasset.dto.audit.FixedAssetLatestAutoAuditDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMachineDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetResolvedLocationDto;
import com.example.patrol_be.fixedasset.dto.zone.FixedAssetZoneProgressDto;

/** Location dropdowns, machines / progress of a zone, MAP lookups and zone lock. */
@Repository
public class FixedAssetLocationRepository {
    private final JdbcTemplate jdbcTemplate;
    private final String div;
    private final String factory;
    private final String mapDivCondition;
    /** MASTER (fa) INNER JOIN MAP (map) on Floor / A / AA within mapDivCondition. */
    private final String joinClause;

    public FixedAssetLocationRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${fixed-asset.div:KVH}") String div,
            @Value("${fixed-asset.factory:Factory 2}") String factory) {
        this.jdbcTemplate = jdbcTemplate;
        this.div = div.trim();
        this.factory = factory.trim();
        this.mapDivCondition = FixedAssetSql.mapDivCondition(this.div);
        this.joinClause = """
                FROM F2Database.dbo.F2_FIXED_ASSET fa
                INNER JOIN F2Database.dbo.F2_FIXED_ASSET_MAP map
                """
                + "    ON " + mapDivCondition + "\n"
                + """
                    AND fa.[Floor] = map.[Floor]
                    AND fa.PositionA = map.A
                    AND fa.PositionAA = map.AA
                """;
    }

    // Dropdowns: effective location (see FixedAssetSql.effectiveMachineSource), same as findMachines / findZoneProgress.

    public List<String> findFacs(
            String pic,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT DISTINCT eff.Fac
                FROM
                """
                + effectiveMachineSource(mapDivCondition, pic)
                + """
                WHERE eff.Fac IS NOT NULL
                    AND eff.Fac <> ''
                ORDER BY eff.Fac
                """;

        return jdbcTemplate.queryForList(
                sql, String.class,
                effectiveMachineParams(div, factory, pic, periodStart, nextPeriodStart).toArray());
    }

    public List<String> findFloors(
            String fac,
            String pic,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT DISTINCT eff.Floor
                FROM
                """
                + effectiveMachineSource(mapDivCondition, pic)
                + """
                WHERE eff.Fac = ?
                    AND eff.Floor IS NOT NULL
                    AND eff.Floor <> ''
                ORDER BY eff.Floor
                """;

        List<Object> params = effectiveMachineParams(div, factory, pic, periodStart, nextPeriodStart);
        params.add(fac);
        return jdbcTemplate.queryForList(sql, String.class, params.toArray());
    }

    public List<String> findPositionA(
            String fac,
            String floor,
            String pic,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT DISTINCT eff.PositionA
                FROM
                """
                + effectiveMachineSource(mapDivCondition, pic)
                + """
                WHERE eff.Fac = ?
                    AND eff.Floor = ?
                    AND eff.PositionA IS NOT NULL
                    AND eff.PositionA <> ''
                ORDER BY eff.PositionA
                """;

        List<Object> params = effectiveMachineParams(div, factory, pic, periodStart, nextPeriodStart);
        params.add(fac);
        params.add(floor);
        return jdbcTemplate.queryForList(sql, String.class, params.toArray());
    }

    public List<String> findPositionAA(
            String fac,
            String floor,
            String positionA,
            String pic,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT DISTINCT eff.PositionAA
                FROM
                """
                + effectiveMachineSource(mapDivCondition, pic)
                + """
                WHERE eff.Fac = ?
                    AND eff.Floor = ?
                    AND eff.PositionA = ?
                    AND eff.PositionAA IS NOT NULL
                    AND eff.PositionAA <> ''
                ORDER BY eff.PositionAA
                """;

        List<Object> params = effectiveMachineParams(div, factory, pic, periodStart, nextPeriodStart);
        params.add(fac);
        params.add(floor);
        params.add(positionA);
        return jdbcTemplate.queryForList(sql, String.class, params.toArray());
    }

    public List<FixedAssetMachineDto> findMachines(
            String fac,
            String floor,
            String positionA,
            String positionAA,
            String pic,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        // Audit filter matches countAuditedMasterMachinesInPeriod / findZoneProgress.
        // Filtered by effective location; one row per machine (MASTER duplicates
        // collapse): moved only when no MASTER row equals the effective location.
        String sql = """
                SELECT
                    eff.MachineCode,
                    eff.FAName,
                    MAX(eff.AuditedInPeriod) AS AuditedInPeriod,
                    MAX(eff.NeedsQrReprint) AS NeedsQrReprint,
                    MIN(CONCAT(eff.MasterFac, '/', eff.MasterFloor, '/',
                        eff.MasterPositionA, '/', eff.MasterPositionAA)) AS MasterLocation,
                    MIN(eff.Moved) AS Moved
                FROM
                """
                + effectiveMachineSource(mapDivCondition, pic)
                + """
                        WHERE eff.Fac = ?
                            AND eff.Floor = ?
                            AND eff.PositionA = ?
                            AND eff.PositionAA = ?
                        GROUP BY eff.MachineCode, eff.FAName
                        ORDER BY eff.MachineCode, eff.FAName
                        """;

        List<Object> params = effectiveMachineParams(div, factory, pic, periodStart, nextPeriodStart);
        params.add(fac);
        params.add(floor);
        params.add(positionA);
        params.add(positionAA);

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetMachineDto(
                        resultSet.getString("MachineCode"),
                        resultSet.getString("FAName"),
                        resultSet.getInt("AuditedInPeriod") == 1,
                        resultSet.getInt("NeedsQrReprint") == 1,
                        resultSet.getString("MasterLocation"),
                        resultSet.getInt("Moved") == 1),
                params.toArray());
    }

    /**
     * Per-zone progress for one Fac + Floor in a single grouped query, by
     * effective location (see {@link FixedAssetSql#effectiveMachineSource}).
     * total: MASTER machines whose effective location is the zone (MASTER
     * location must be mapped when not audited). audited: same audit filter as
     * {@link FixedAssetSummaryRepository#countAuditedMasterMachinesInPeriod(LocalDateTime, LocalDateTime, String)}.
     */
    public List<FixedAssetZoneProgressDto> findZoneProgress(
            String fac,
            String floor,
            String pic,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT
                    eff.PositionA,
                    eff.PositionAA,
                    COUNT(DISTINCT eff.MachineCode) AS Total,
                    COUNT(DISTINCT CASE WHEN eff.AuditedInPeriod = 1
                        THEN eff.MachineCode END) AS Audited
                FROM
                """
                + effectiveMachineSource(mapDivCondition, pic)
                + """
                        WHERE eff.Fac = ?
                            AND eff.Floor = ?
                            AND eff.PositionA IS NOT NULL
                            AND eff.PositionA <> ''
                            AND eff.PositionAA IS NOT NULL
                            AND eff.PositionAA <> ''
                        GROUP BY
                            eff.PositionA,
                            eff.PositionAA
                        ORDER BY PositionA, PositionAA
                        """;

        List<Object> params = effectiveMachineParams(div, factory, pic, periodStart, nextPeriodStart);
        params.add(fac);
        params.add(floor);

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetZoneProgressDto(
                        resultSet.getString("PositionA"),
                        resultSet.getString("PositionAA"),
                        resultSet.getLong("Total"),
                        resultSet.getLong("Audited")),
                params.toArray());
    }

    public List<FixedAssetResolvedLocationDto> findLocationsByFloorAndPositionAA(
            String floor,
            String positionAA) {
        String sql = """
                SELECT DISTINCT
                    LTRIM(RTRIM(map.Fac)) AS Fac,
                    LTRIM(RTRIM(map.[Floor])) AS Floor,
                    LTRIM(RTRIM(map.A)) AS PositionA,
                    LTRIM(RTRIM(map.AA)) AS PositionAA
                FROM F2Database.dbo.F2_FIXED_ASSET_MAP map
                """
                + "WHERE " + mapDivCondition + "\n"
                + """
                    AND LTRIM(RTRIM(map.[Floor])) = ?
                    AND LTRIM(RTRIM(map.AA)) = ?
                ORDER BY Fac, Floor, PositionA, PositionAA
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetResolvedLocationDto(
                        resultSet.getString("Fac"),
                        resultSet.getString("Floor"),
                        resultSet.getString("PositionA"),
                        resultSet.getString("PositionAA")),
                floor,
                positionAA);
    }

    public List<FixedAssetResolvedLocationDto> findLocationsByPositionAAndAA(
            String positionA,
            String positionAA) {
        String sql = """
                SELECT DISTINCT
                    LTRIM(RTRIM(map.Fac)) AS Fac,
                    LTRIM(RTRIM(map.[Floor])) AS Floor,
                    LTRIM(RTRIM(map.A)) AS PositionA,
                    LTRIM(RTRIM(map.AA)) AS PositionAA
                FROM F2Database.dbo.F2_FIXED_ASSET_MAP map
                """
                + "WHERE " + mapDivCondition + "\n"
                + """
                    AND LTRIM(RTRIM(map.A)) = ?
                    AND LTRIM(RTRIM(map.AA)) = ?
                ORDER BY Fac, Floor, PositionA, PositionAA
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetResolvedLocationDto(
                        resultSet.getString("Fac"),
                        resultSet.getString("Floor"),
                        resultSet.getString("PositionA"),
                        resultSet.getString("PositionAA")),
                positionA,
                positionAA);
    }

    public boolean existsLocation(
            String fac,
            String floor,
            String positionA,
            String positionAA) {
        String sql = """
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                """ + joinClause
                + "    WHERE " + SCOPE_CONDITION + "\n"
                + """
                        AND map.Fac = ?
                        AND fa.[Floor] = ?
                        AND fa.PositionA = ?
                        AND fa.PositionAA = ?
                ) THEN 1 ELSE 0 END
                """;

        Integer result = jdbcTemplate.queryForObject(
                sql, Integer.class, div, factory, fac, floor, positionA, positionAA);
        return result != null && result == 1;
    }

    /** Zone lock: latest AUTO audit of the user in the period (with A / AA). */
    public FixedAssetLatestAutoAuditDto findLatestAutoAuditInPeriod(
            String userId,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT TOP 1
                    LTRIM(RTRIM(A_Act)) AS PositionA,
                    LTRIM(RTRIM(AA_Act)) AS PositionAA,
                    UpdatedAt
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT
                WHERE UserId IS NOT NULL
                    AND LTRIM(RTRIM(UserId)) = ?
                    AND AuditMode = 'AUTO'
                    AND A_Act IS NOT NULL
                    AND LTRIM(RTRIM(A_Act)) <> ''
                    AND AA_Act IS NOT NULL
                    AND LTRIM(RTRIM(AA_Act)) <> ''
                    AND UpdatedAt >= ?
                    AND UpdatedAt < ?
                ORDER BY UpdatedAt DESC, Id DESC
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetLatestAutoAuditDto(
                        resultSet.getString("PositionA"),
                        resultSet.getString("PositionAA"),
                        resultSet.getTimestamp("UpdatedAt").toLocalDateTime()),
                userId,
                Timestamp.valueOf(periodStart),
                Timestamp.valueOf(nextPeriodStart)).stream().findFirst().orElse(null);
    }
}
