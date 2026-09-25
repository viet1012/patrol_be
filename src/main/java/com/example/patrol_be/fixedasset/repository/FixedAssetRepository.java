package com.example.patrol_be.fixedasset.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.patrol_be.fixedasset.dto.FixedAssetMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineLocationDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMasterMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetResolvedLocationDto;

@Repository
public class FixedAssetRepository {
    private static final String JOIN_CLAUSE = """
            FROM F2Database.dbo.F2_FIXED_ASSET fa
            INNER JOIN F2Database.dbo.F2_FIXED_ASSET_MAP map
                ON fa.Div = map.Div
                AND fa.[Floor] = map.[Floor]
                AND fa.PositionA = map.A
                AND fa.PositionAA = map.AA
            """;

    private final JdbcTemplate jdbcTemplate;

    public FixedAssetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findFacs() {
        String sql = """
                SELECT DISTINCT map.Fac
                """ + JOIN_CLAUSE + """
                WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                    AND map.Fac IS NOT NULL
                    AND LTRIM(RTRIM(map.Fac)) <> ''
                ORDER BY map.Fac
                """;

        return jdbcTemplate.queryForList(sql, String.class);
    }

    public List<String> findFloors(String fac) {
        String sql = """
                SELECT DISTINCT fa.[Floor]
                """ + JOIN_CLAUSE + """
                WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                    AND map.Fac = ?
                    AND fa.[Floor] IS NOT NULL
                    AND LTRIM(RTRIM(fa.[Floor])) <> ''
                ORDER BY fa.[Floor]
                """;

        return jdbcTemplate.queryForList(sql, String.class, fac);
    }

    public List<String> findPositionA(String fac, String floor) {
        String sql = """
                SELECT DISTINCT fa.PositionA
                """ + JOIN_CLAUSE + """
                WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                    AND map.Fac = ?
                    AND fa.[Floor] = ?
                    AND fa.PositionA IS NOT NULL
                    AND LTRIM(RTRIM(fa.PositionA)) <> ''
                ORDER BY fa.PositionA
                """;

        return jdbcTemplate.queryForList(sql, String.class, fac, floor);
    }

    public List<String> findPositionAA(String fac, String floor, String positionA) {
        String sql = """
                SELECT DISTINCT fa.PositionAA
                """ + JOIN_CLAUSE + """
                WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                    AND map.Fac = ?
                    AND fa.[Floor] = ?
                    AND fa.PositionA = ?
                    AND fa.PositionAA IS NOT NULL
                    AND LTRIM(RTRIM(fa.PositionAA)) <> ''
                ORDER BY fa.PositionAA
                """;

        return jdbcTemplate.queryForList(sql, String.class, fac, floor, positionA);
    }

    public List<FixedAssetMachineDto> findMachines(
            String fac,
            String floor,
            String positionA,
            String positionAA
    ) {
        String sql = """
                SELECT DISTINCT fa.MachineCode, fa.FAName
                """ + JOIN_CLAUSE + """
                WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                    AND map.Fac = ?
                    AND fa.[Floor] = ?
                    AND fa.PositionA = ?
                    AND fa.PositionAA = ?
                    AND fa.MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(fa.MachineCode)) <> ''
                ORDER BY fa.MachineCode, fa.FAName
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetMachineDto(
                        resultSet.getString("MachineCode"),
                        resultSet.getString("FAName")
                ),
                fac,
                floor,
                positionA,
                positionAA
        );
    }

    /**
     * Authoritative MASTER lookup: LEFT JOIN keeps the F2_FIXED_ASSET row when
     * its location has no MAP row, while resolving Fac when a mapping exists.
     * Physical duplicates with the same logical location collapse into one row.
     */
    public List<FixedAssetMasterMachineDto> findMasterMachinesByCode(String machineCode) {
        String sql = """
                SELECT
                    LTRIM(RTRIM(map.Fac)) AS Fac,
                    LTRIM(RTRIM(fa.Div)) AS Div,
                    LTRIM(RTRIM(fa.MachineCode)) AS MachineCode,
                    LTRIM(RTRIM(fa.[Floor])) AS Floor,
                    LTRIM(RTRIM(fa.PositionA)) AS PositionA,
                    LTRIM(RTRIM(fa.PositionAA)) AS PositionAA,
                    MAX(fa.FAName) AS FAName
                FROM F2Database.dbo.F2_FIXED_ASSET fa
                LEFT JOIN F2Database.dbo.F2_FIXED_ASSET_MAP map
                    ON fa.Div = map.Div
                    AND fa.[Floor] = map.[Floor]
                    AND fa.PositionA = map.A
                    AND fa.PositionAA = map.AA
                WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                    AND fa.MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(fa.MachineCode)) = ?
                GROUP BY
                    LTRIM(RTRIM(map.Fac)),
                    LTRIM(RTRIM(fa.Div)),
                    LTRIM(RTRIM(fa.MachineCode)),
                    LTRIM(RTRIM(fa.[Floor])),
                    LTRIM(RTRIM(fa.PositionA)),
                    LTRIM(RTRIM(fa.PositionAA))
                ORDER BY Floor, PositionA, PositionAA, Fac
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetMasterMachineDto(
                        resultSet.getString("Fac"),
                        resultSet.getString("Div"),
                        resultSet.getString("MachineCode"),
                        resultSet.getString("Floor"),
                        resultSet.getString("PositionA"),
                        resultSet.getString("PositionAA"),
                        resultSet.getString("FAName")
                ),
                machineCode
        );
    }

    /**
     * Joined MASTER + MAP lookup. NOT an existence test: a machine whose MASTER
     * location has no exact MAP row is missing here. Use
     * {@link #findMasterMachinesByCode(String)} to decide MASTER existence.
     */
    public List<FixedAssetMachineLocationDto> findMachineLocationsByCode(
            String div,
            String machineCode
    ) {
        String sql = """
                SELECT
                    map.Fac AS Fac,
                    fa.[Floor] AS Floor,
                    fa.PositionA AS PositionA,
                    fa.PositionAA AS PositionAA,
                    LTRIM(RTRIM(fa.MachineCode)) AS MachineCode,
                    MAX(fa.FAName) AS FAName
                """ + JOIN_CLAUSE + """
                WHERE LTRIM(RTRIM(fa.Div)) = ?
                    AND LTRIM(RTRIM(fa.MachineCode)) = ?
                GROUP BY
                    map.Fac,
                    fa.[Floor],
                    fa.PositionA,
                    fa.PositionAA,
                    LTRIM(RTRIM(fa.MachineCode))
                ORDER BY
                    map.Fac,
                    fa.[Floor],
                    fa.PositionA,
                    fa.PositionAA
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetMachineLocationDto(
                        resultSet.getString("Fac"),
                        resultSet.getString("Floor"),
                        resultSet.getString("PositionA"),
                        resultSet.getString("PositionAA"),
                        resultSet.getString("MachineCode"),
                        resultSet.getString("FAName")
                ),
                div,
                machineCode
        );
    }

    public List<FixedAssetResolvedLocationDto> findLocationsByFloorAndPositionAA(
            String floor,
            String positionAA
    ) {
        String sql = """
                SELECT DISTINCT
                    LTRIM(RTRIM(map.Fac)) AS Fac,
                    LTRIM(RTRIM(map.[Floor])) AS Floor,
                    LTRIM(RTRIM(map.A)) AS PositionA,
                    LTRIM(RTRIM(map.AA)) AS PositionAA
                FROM F2Database.dbo.F2_FIXED_ASSET_MAP map
                WHERE LTRIM(RTRIM(map.Div)) = 'KVH'
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
                        resultSet.getString("PositionAA")
                ),
                floor,
                positionAA
        );
    }

    public LocalDateTime findLatestAuditInPeriod(
            String machineCode,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart
    ) {
        String sql = """
                SELECT MAX(UpdatedAt) AS LastAuditedAt
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT
                WHERE MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(MachineCode)) = ?
                    AND UpdatedAt >= ?
                    AND UpdatedAt < ?
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (resultSet, rowNum) -> {
                    Timestamp value = resultSet.getTimestamp("LastAuditedAt");
                    return value == null ? null : value.toLocalDateTime();
                },
                machineCode,
                Timestamp.valueOf(periodStart),
                Timestamp.valueOf(nextPeriodStart)
        );
    }

    public LocalDateTime findLatestAuditInPeriodForUpdate(
            String machineCode,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart
    ) {
        String sql = """
                SELECT MAX(UpdatedAt) AS LastAuditedAt
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT WITH (UPDLOCK, HOLDLOCK)
                WHERE MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(MachineCode)) = ?
                    AND UpdatedAt >= ?
                    AND UpdatedAt < ?
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (resultSet, rowNum) -> {
                    Timestamp value = resultSet.getTimestamp("LastAuditedAt");
                    return value == null ? null : value.toLocalDateTime();
                },
                machineCode,
                Timestamp.valueOf(periodStart),
                Timestamp.valueOf(nextPeriodStart)
        );
    }

    public long countMasterMachines() {
        String sql = """
                SELECT COUNT(DISTINCT LTRIM(RTRIM(MachineCode)))
                FROM F2Database.dbo.F2_FIXED_ASSET
                WHERE LTRIM(RTRIM(Div)) = 'KVH'
                    AND MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(MachineCode)) <> ''
                """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    public long countAuditedMasterMachinesInPeriod(
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart
    ) {
        String sql = """
                SELECT COUNT(DISTINCT LTRIM(RTRIM(a.MachineCode)))
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT a
                WHERE a.MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(a.MachineCode)) <> ''
                    AND a.UpdatedAt >= ?
                    AND a.UpdatedAt < ?
                    AND EXISTS (
                        SELECT 1
                        FROM F2Database.dbo.F2_FIXED_ASSET fa
                        WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                            AND LTRIM(RTRIM(fa.MachineCode)) = LTRIM(RTRIM(a.MachineCode))
                    )
                """;

        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                Timestamp.valueOf(periodStart),
                Timestamp.valueOf(nextPeriodStart)
        );
        return count == null ? 0 : count;
    }

    public boolean existsLocation(
            String fac,
            String floor,
            String positionA,
            String positionAA
    ) {
        String sql = """
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                """ + JOIN_CLAUSE + """
                    WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                        AND map.Fac = ?
                        AND fa.[Floor] = ?
                        AND fa.PositionA = ?
                        AND fa.PositionAA = ?
                ) THEN 1 ELSE 0 END
                """;

        Integer result = jdbcTemplate.queryForObject(
                sql, Integer.class, fac, floor, positionA, positionAA);
        return result != null && result == 1;
    }

    public boolean existsMachine(String div, String machineCode) {
        String sql = """
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM F2Database.dbo.F2_FIXED_ASSET
                    WHERE LTRIM(RTRIM(Div)) = ?
                        AND LTRIM(RTRIM(MachineCode)) = ?
                ) THEN 1 ELSE 0 END
                """;

        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, div, machineCode);
        return result != null && result == 1;
    }

    public int insertAudit(
            String machineCode,
            String positionA,
            String positionAA,
            String userId,
            String userName,
            LocalDateTime updatedAt,
            String note
    ) {
        String sql = """
                INSERT INTO F2Database.dbo.F2_FIXED_ASSET_AUDIT
                (
                    MachineCode,
                    A_Act,
                    AA_Act,
                    UserId,
                    UserName,
                    UpdatedAt,
                    Note
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        return jdbcTemplate.update(
                sql,
                machineCode,
                positionA,
                positionAA,
                userId,
                userName,
                Timestamp.valueOf(updatedAt),
                note
        );
    }

    public List<String> findAuditedMachineCodes() {
        String sql = """
                SELECT DISTINCT
                    LTRIM(RTRIM(MachineCode)) AS MachineCode
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT
                WHERE MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(MachineCode)) <> ''
                ORDER BY MachineCode
                """;

        return jdbcTemplate.queryForList(sql, String.class);
    }
}
