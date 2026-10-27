package com.example.patrol_be.fixedasset.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.patrol_be.fixedasset.dto.FixedAssetMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMachineLocationDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetMasterMachineDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetResolvedLocationDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetLatestAuditDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetLatestAutoAuditDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetQrMismatchDto;
import com.example.patrol_be.fixedasset.dto.FixedAssetZoneProgressDto;

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

    /**
     * Latest audit row per machine in the period (UpdatedAt DESC, Id DESC).
     * Params: periodStart, nextPeriodStart. Any row in the period counts as
     * audited; QrMismatch only drives needsQrReprint (label status).
     */
    private static final String LATEST_AUDIT_IN_PERIOD = """
            (
                SELECT ranked.MachineCode, ranked.QrMismatch
                FROM (
                    SELECT
                        LTRIM(RTRIM(a.MachineCode)) AS MachineCode,
                        a.QrMismatch,
                        ROW_NUMBER() OVER (
                            PARTITION BY LTRIM(RTRIM(a.MachineCode))
                            ORDER BY a.UpdatedAt DESC, a.Id DESC
                        ) AS rn
                    FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT a
                    WHERE a.MachineCode IS NOT NULL
                        AND LTRIM(RTRIM(a.MachineCode)) <> ''
                        AND a.UpdatedAt >= ?
                        AND a.UpdatedAt < ?
                ) ranked
                WHERE ranked.rn = 1
            )
            """;

    private static final String AUDITED_CONDITION = "latest.MachineCode IS NOT NULL";
    private static final String NEEDS_QR_REPRINT_CONDITION = "latest.MachineCode IS NOT NULL AND ISNULL(latest.QrMismatch, 0) = 1";

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
            String positionAA,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        // Audit filter matches countAuditedMasterMachinesInPeriod / findZoneProgress.
        String sql = """
                SELECT DISTINCT
                    fa.MachineCode,
                    fa.FAName,
                """
                + "    CASE WHEN " + AUDITED_CONDITION + " THEN 1 ELSE 0 END AS AuditedInPeriod,\n"
                + "    CASE WHEN " + NEEDS_QR_REPRINT_CONDITION + " THEN 1 ELSE 0 END AS NeedsQrReprint\n"
                + JOIN_CLAUSE
                + "LEFT JOIN " + LATEST_AUDIT_IN_PERIOD + " latest\n"
                + """
                            ON latest.MachineCode = LTRIM(RTRIM(fa.MachineCode))
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
                        resultSet.getString("FAName"),
                        resultSet.getInt("AuditedInPeriod") == 1,
                        resultSet.getInt("NeedsQrReprint") == 1),
                Timestamp.valueOf(periodStart),
                Timestamp.valueOf(nextPeriodStart),
                fac,
                floor,
                positionA,
                positionAA);
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
                        resultSet.getString("FAName")),
                machineCode);
    }

    /**
     * Joined MASTER + MAP lookup. NOT an existence test: a machine whose MASTER
     * location has no exact MAP row is missing here. Use
     * {@link #findMasterMachinesByCode(String)} to decide MASTER existence.
     */
    public List<FixedAssetMachineLocationDto> findMachineLocationsByCode(
            String div,
            String machineCode) {
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
                        resultSet.getString("FAName")),
                div,
                machineCode);
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
                        resultSet.getString("PositionAA")),
                floor,
                positionAA);
    }

    public FixedAssetLatestAuditDto findLatestAuditInPeriod(
            String machineCode,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT TOP 1
                    Id,
                    UserId,
                    UserName,
                    UpdatedAt,
                    QrMismatch
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT
                WHERE MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(MachineCode)) = ?
                    AND UpdatedAt >= ?
                    AND UpdatedAt < ?
                ORDER BY UpdatedAt DESC, Id DESC
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> mapLatestAudit(resultSet),
                machineCode,
                Timestamp.valueOf(periodStart),
                Timestamp.valueOf(nextPeriodStart)).stream().findFirst().orElse(null);
    }

    /**
     * Same latest row as {@link #findLatestAuditInPeriod}, read under
     * UPDLOCK/HOLDLOCK so concurrent saves of the same machine serialize.
     */
    public FixedAssetLatestAuditDto findLatestAuditInPeriodForUpdate(
            String machineCode,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT TOP 1
                    Id,
                    UserId,
                    UserName,
                    UpdatedAt,
                    QrMismatch
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT WITH (UPDLOCK, HOLDLOCK)
                WHERE MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(MachineCode)) = ?
                    AND UpdatedAt >= ?
                    AND UpdatedAt < ?
                ORDER BY UpdatedAt DESC, Id DESC
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> mapLatestAudit(resultSet),
                machineCode,
                Timestamp.valueOf(periodStart),
                Timestamp.valueOf(nextPeriodStart)).stream().findFirst().orElse(null);
    }

    /** needsQrReprint = latest row has QrMismatch = 1 (NULL treated as 0). */
    private FixedAssetLatestAuditDto mapLatestAudit(ResultSet resultSet) throws SQLException {
        return new FixedAssetLatestAuditDto(
                resultSet.getLong("Id"),
                resultSet.getString("UserId"),
                resultSet.getString("UserName"),
                resultSet.getTimestamp("UpdatedAt").toLocalDateTime(),
                resultSet.getBoolean("QrMismatch"));
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
            LocalDateTime nextPeriodStart) {
        String sql = "SELECT COUNT(DISTINCT latest.MachineCode)\n"
                + "FROM " + LATEST_AUDIT_IN_PERIOD + " latest\n"
                + "WHERE " + AUDITED_CONDITION + "\n"
                + """
                            AND EXISTS (
                                SELECT 1
                                FROM F2Database.dbo.F2_FIXED_ASSET fa
                                WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                                    AND LTRIM(RTRIM(fa.MachineCode)) = latest.MachineCode
                            )
                        """;

        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                Timestamp.valueOf(periodStart),
                Timestamp.valueOf(nextPeriodStart));
        return count == null ? 0 : count;
    }

    /**
     * Per-zone progress for one Fac + Floor in a single grouped query.
     * total: same MASTER population as {@link #countMasterMachines()}, limited
     * to mapped locations. audited: same audit filter as
     * {@link #countAuditedMasterMachinesInPeriod(LocalDateTime, LocalDateTime)}.
     */
    public List<FixedAssetZoneProgressDto> findZoneProgress(
            String fac,
            String floor,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        String sql = """
                SELECT
                    LTRIM(RTRIM(fa.PositionA)) AS PositionA,
                    LTRIM(RTRIM(fa.PositionAA)) AS PositionAA,
                    COUNT(DISTINCT LTRIM(RTRIM(fa.MachineCode))) AS Total,
                """
                + "    COUNT(DISTINCT CASE WHEN " + AUDITED_CONDITION
                + " THEN latest.MachineCode END) AS Audited\n"
                + JOIN_CLAUSE
                + "LEFT JOIN " + LATEST_AUDIT_IN_PERIOD + " latest\n"
                + """
                            ON latest.MachineCode = LTRIM(RTRIM(fa.MachineCode))
                        WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                            AND map.Fac = ?
                            AND fa.[Floor] = ?
                            AND fa.MachineCode IS NOT NULL
                            AND LTRIM(RTRIM(fa.MachineCode)) <> ''
                            AND fa.PositionA IS NOT NULL
                            AND LTRIM(RTRIM(fa.PositionA)) <> ''
                            AND fa.PositionAA IS NOT NULL
                            AND LTRIM(RTRIM(fa.PositionAA)) <> ''
                        GROUP BY
                            LTRIM(RTRIM(fa.PositionA)),
                            LTRIM(RTRIM(fa.PositionAA))
                        ORDER BY PositionA, PositionAA
                        """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetZoneProgressDto(
                        resultSet.getString("PositionA"),
                        resultSet.getString("PositionAA"),
                        resultSet.getLong("Total"),
                        resultSet.getLong("Audited")),
                Timestamp.valueOf(periodStart),
                Timestamp.valueOf(nextPeriodStart),
                fac,
                floor);
    }

    public boolean existsLocation(
            String fac,
            String floor,
            String positionA,
            String positionAA) {
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
            String note,
            String auditMode,
            String qrFloor,
            String qrPositionA,
            String qrPositionAA,
            boolean qrMismatch) {
        String sql = """
                INSERT INTO F2Database.dbo.F2_FIXED_ASSET_AUDIT
                (
                    MachineCode,
                    A_Act,
                    AA_Act,
                    UserId,
                    UserName,
                    UpdatedAt,
                    Note,
                    AuditMode,
                    Floor_Qr,
                    A_Qr,
                    AA_Qr,
                    QrMismatch
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        return jdbcTemplate.update(
                sql,
                machineCode,
                positionA,
                positionAA,
                userId,
                userName,
                Timestamp.valueOf(updatedAt),
                note,
                auditMode,
                qrFloor,
                qrPositionA,
                qrPositionAA,
                qrMismatch ? 1 : 0);
    }

    /**
     * Flags an existing audit row for QR reprint (no new row). QR location is
     * overwritten only when sent; audit user / time are kept as audited.
     */
    public int markAuditQrReprint(
            long id,
            String qrFloor,
            String qrPositionA,
            String qrPositionAA) {
        String sql = """
                UPDATE F2Database.dbo.F2_FIXED_ASSET_AUDIT
                SET QrMismatch = 1,
                    Floor_Qr = COALESCE(?, Floor_Qr),
                    A_Qr = COALESCE(?, A_Qr),
                    AA_Qr = COALESCE(?, AA_Qr)
                WHERE Id = ?
                """;

        return jdbcTemplate.update(sql, qrFloor, qrPositionA, qrPositionAA, id);
    }

    /**
     * Clears the QR reprint flag of an existing audit row (QR now matches) and
     * moves its ACTUAL location to the selected one. The table has no actual
     * Floor / Fac column, only A_Act / AA_Act. Audit user / time are kept.
     */
    public int clearAuditQrReprint(
            long id,
            String positionA,
            String positionAA) {
        String sql = """
                UPDATE F2Database.dbo.F2_FIXED_ASSET_AUDIT
                SET QrMismatch = 0,
                    Floor_Qr = NULL,
                    A_Qr = NULL,
                    AA_Qr = NULL,
                    A_Act = ?,
                    AA_Act = ?
                WHERE Id = ?
                """;

        return jdbcTemplate.update(sql, positionA, positionAA, id);
    }

    /**
     * Machines whose latest audit in the period has QrMismatch = 1 (QR label
     * needs reprinting).
     * FAName / Fac come from MASTER (LEFT JOIN MAP, as findMasterMachinesByCode);
     * the fac filter applies to the MASTER Fac.
     */
    public List<FixedAssetQrMismatchDto> findQrMismatchAudits(
            String fac,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        boolean filterFac = fac != null && !fac.isBlank();
        String sql = """
                SELECT
                    LTRIM(RTRIM(a.MachineCode)) AS MachineCode,
                    m.FAName,
                    m.AssetDate,
                    a.A_Act,
                    a.AA_Act,
                    a.Floor_Qr,
                    a.A_Qr,
                    a.AA_Qr,
                    a.UserName,
                    a.UpdatedAt
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT a
                OUTER APPLY (
                    SELECT TOP 1
                        fa.FAName,
                        TRY_CONVERT(date, fa.DateStart) AS AssetDate,
                        LTRIM(RTRIM(map.Fac)) AS Fac
                    FROM F2Database.dbo.F2_FIXED_ASSET fa
                    LEFT JOIN F2Database.dbo.F2_FIXED_ASSET_MAP map
                        ON fa.Div = map.Div
                        AND fa.[Floor] = map.[Floor]
                        AND fa.PositionA = map.A
                        AND fa.PositionAA = map.AA
                    WHERE LTRIM(RTRIM(fa.Div)) = 'KVH'
                        AND LTRIM(RTRIM(fa.MachineCode)) = LTRIM(RTRIM(a.MachineCode))
                    ORDER BY map.Fac DESC
                ) m
                WHERE a.QrMismatch = 1
                    AND a.MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(a.MachineCode)) <> ''
                    AND a.UpdatedAt >= ?
                    AND a.UpdatedAt < ?
                    -- only the machine's latest row in the period (legacy data
                    -- may hold several rows per machine)
                    AND NOT EXISTS (
                        SELECT 1
                        FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT b
                        WHERE LTRIM(RTRIM(b.MachineCode)) = LTRIM(RTRIM(a.MachineCode))
                            AND b.UpdatedAt < ?
                            AND (b.UpdatedAt > a.UpdatedAt
                                OR (b.UpdatedAt = a.UpdatedAt AND b.Id > a.Id))
                    )
                """ + (filterFac ? "    AND m.Fac = ?\n" : "") + """
                ORDER BY a.UpdatedAt DESC, a.Id DESC
                """;

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(periodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));
        if (filterFac) {
            params.add(fac.trim());
        }

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetQrMismatchDto(
                        resultSet.getString("MachineCode"),
                        resultSet.getDate("AssetDate") != null ? resultSet.getDate("AssetDate").toLocalDate() : null,
                        resultSet.getString("FAName"),
                        resultSet.getString("A_Act"),
                        resultSet.getString("AA_Act"),
                        resultSet.getString("Floor_Qr"),
                        resultSet.getString("A_Qr"),
                        resultSet.getString("AA_Qr"),
                        resultSet.getString("UserName"),
                        resultSet.getTimestamp("UpdatedAt").toLocalDateTime()),
                params.toArray());
    }

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
                WHERE LTRIM(RTRIM(map.Div)) = 'KVH'
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

    public List<String> findAuditedMachineCodes() {
        // No period filter (unchanged since the first version); QrMismatch rows
        // count as audited.
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
