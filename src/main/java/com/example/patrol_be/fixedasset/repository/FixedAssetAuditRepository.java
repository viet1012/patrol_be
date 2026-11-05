package com.example.patrol_be.fixedasset.repository;

import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.PIC_EXPR;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.SCOPE_CONDITION;
import static com.example.patrol_be.fixedasset.repository.FixedAssetSql.toLocalDateTime;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.example.patrol_be.fixedasset.dto.audit.FixedAssetAuditPhotoTargetDto;
import com.example.patrol_be.fixedasset.dto.audit.FixedAssetLatestAuditDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMasterMachineDto;
import com.example.patrol_be.fixedasset.dto.machine.FixedAssetMasterPhotoDto;
import com.example.patrol_be.fixedasset.dto.scan.FixedAssetQrMismatchDto;

/** Scan (MASTER lookup), audit check / save, latest audit, QR mismatch and reprint. */
@Repository
public class FixedAssetAuditRepository {
    private final JdbcTemplate jdbcTemplate;
    private final String div;
    private final String factory;
    private final String mapDivCondition;

    public FixedAssetAuditRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${fixed-asset.div:KVH}") String div,
            @Value("${fixed-asset.factory:Factory 2}") String factory) {
        this.jdbcTemplate = jdbcTemplate;
        this.div = div.trim();
        this.factory = factory.trim();
        this.mapDivCondition = FixedAssetSql.mapDivCondition(this.div);
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
                    MAX(fa.FAName) AS FAName,
                """
                + "    MAX(NULLIF(" + PIC_EXPR + ", '')) AS Pic\n"
                + """
                FROM F2Database.dbo.F2_FIXED_ASSET fa
                LEFT JOIN F2Database.dbo.F2_FIXED_ASSET_MAP map
                """
                + "    ON " + mapDivCondition + "\n"
                + """
                    AND fa.[Floor] = map.[Floor]
                    AND fa.PositionA = map.A
                    AND fa.PositionAA = map.AA
                """
                + "WHERE " + SCOPE_CONDITION + "\n"
                + """
                    AND fa.MachineCode IS NOT NULL
                    AND UPPER(LTRIM(RTRIM(fa.MachineCode))) = UPPER(LTRIM(RTRIM(?)))
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
                        resultSet.getString("FAName"),
                        resultSet.getString("Pic")),
                div,
                factory,
                machineCode);
    }

    /** Photo columns of the machine's MASTER row in scope; null when not in MASTER. */
    public FixedAssetMasterPhotoDto findMasterPhoto(String machineCode) {
        String sql = """
                SELECT TOP 1
                    LTRIM(RTRIM(fa.Div)) AS Div,
                    LTRIM(RTRIM(fa.MachineCode)) AS MachineCode,
                    LTRIM(RTRIM(fa.[Group])) AS [Group],
                    fa.Photo3,
                    fa.Photo3_Old,
                    LTRIM(RTRIM(fa.[Floor])) AS Floor,
                    LTRIM(RTRIM(fa.PositionAA)) AS PositionAA
                FROM F2Database.dbo.F2_FIXED_ASSET fa
                """
                + "WHERE " + SCOPE_CONDITION + "\n"
                + """
                    AND fa.MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(fa.MachineCode)) = ?
                ORDER BY fa.Div, fa.[Group]
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetMasterPhotoDto(
                        resultSet.getString("Div"),
                        resultSet.getString("MachineCode"),
                        resultSet.getString("Group"),
                        resultSet.getString("Photo3"),
                        resultSet.getString("Photo3_Old"),
                        resultSet.getString("Floor"),
                        resultSet.getString("PositionAA")),
                div,
                factory,
                machineCode).stream().findFirst().orElse(null);
    }

    /**
     * SET part of the "3) Thay QR" photo on audit row a (MASTER is only read):
     * Photo3 = new "after/{file}", Photo3_Old = current MASTER Photo3 of the
     * row's machine (first row in scope, as findMasterPhoto; NULL when none /
     * blank). Params: photo3, div, factory.
     */
    private static final String AUDIT_PHOTO3_SET = ",\n"
            + "    Photo3 = ?,\n"
            + "    Photo3_Old = (\n"
            + "        SELECT TOP 1 NULLIF(LTRIM(RTRIM(fa.Photo3)), '')\n"
            + "        FROM F2Database.dbo.F2_FIXED_ASSET fa\n"
            + "        WHERE " + SCOPE_CONDITION + "\n"
            + "            AND fa.MachineCode IS NOT NULL\n"
            + "            AND LTRIM(RTRIM(fa.MachineCode)) = LTRIM(RTRIM(a.MachineCode))\n"
            + "        ORDER BY fa.Div, fa.[Group]\n"
            + "    )\n";

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
                    QrMismatch,
                    ReprintSts,
                    ReprintAt,
                    Fac_Act,
                    Floor_Act,
                    A_Act,
                    AA_Act,
                    Fac_Qr,
                    Floor_Qr,
                    A_Qr,
                    AA_Qr
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
                    QrMismatch,
                    ReprintSts,
                    ReprintAt,
                    Fac_Act,
                    Floor_Act,
                    A_Act,
                    AA_Act,
                    Fac_Qr,
                    Floor_Qr,
                    A_Qr,
                    AA_Qr
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

    /** needsQrReprint = QrMismatch = 1 AND ReprintSts = 0 (NULL treated as 0). */
    private FixedAssetLatestAuditDto mapLatestAudit(ResultSet resultSet) throws SQLException {
        boolean reprintSts = resultSet.getBoolean("ReprintSts");
        return new FixedAssetLatestAuditDto(
                resultSet.getLong("Id"),
                resultSet.getString("UserId"),
                resultSet.getString("UserName"),
                resultSet.getTimestamp("UpdatedAt").toLocalDateTime(),
                resultSet.getBoolean("QrMismatch") && !reprintSts,
                reprintSts,
                toLocalDateTime(resultSet.getTimestamp("ReprintAt")),
                trimmed(resultSet.getString("Fac_Act")),
                trimmed(resultSet.getString("Floor_Act")),
                trimmed(resultSet.getString("A_Act")),
                trimmed(resultSet.getString("AA_Act")),
                trimmed(resultSet.getString("Fac_Qr")),
                trimmed(resultSet.getString("Floor_Qr")),
                trimmed(resultSet.getString("A_Qr")),
                trimmed(resultSet.getString("AA_Qr")));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    /** Id / MachineCode (trimmed) / creator UserId (trimmed) of one audit row; null when not found. */
    public FixedAssetAuditPhotoTargetDto findAuditPhotoTarget(long id) {
        String sql = """
                SELECT
                    Id,
                    LTRIM(RTRIM(MachineCode)) AS MachineCode,
                    LTRIM(RTRIM(UserId)) AS UserId
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT
                WHERE Id = ?
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetAuditPhotoTargetDto(
                        resultSet.getLong("Id"),
                        resultSet.getString("MachineCode"),
                        resultSet.getString("UserId")),
                id).stream().findFirst().orElse(null);
    }

    /** Id of the inserted row; null when nothing was inserted. */
    public Long insertAudit(
            String machineCode,
            String facAct,
            String floorAct,
            String positionA,
            String positionAA,
            String userId,
            String userName,
            LocalDateTime updatedAt,
            String note,
            String auditMode,
            String qrFac,
            String qrFloor,
            String qrPositionA,
            String qrPositionAA,
            boolean qrMismatch,
            String pic) {
        // Pic: MASTER EmailChecked at audit time (normalized; null when not in MASTER / blank).
        String sql = """
                INSERT INTO F2Database.dbo.F2_FIXED_ASSET_AUDIT
                (
                    MachineCode,
                    Fac_Act,
                    Floor_Act,
                    A_Act,
                    AA_Act,
                    UserId,
                    UserName,
                    UpdatedAt,
                    Note,
                    AuditMode,
                    Fac_Qr,
                    Floor_Qr,
                    A_Qr,
                    AA_Qr,
                    QrMismatch,
                    Pic
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        ArgumentPreparedStatementSetter args = new ArgumentPreparedStatementSetter(new Object[] {
                machineCode,
                facAct,
                floorAct,
                positionA,
                positionAA,
                userId,
                userName,
                Timestamp.valueOf(updatedAt),
                note,
                auditMode,
                qrFac,
                qrFloor,
                qrPositionA,
                qrPositionAA,
                qrMismatch ? 1 : 0,
                pic});
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            args.setValues(statement);
            return statement;
        }, keyHolder);

        Number id = keyHolder.getKey();
        return inserted == 1 && id != null ? id.longValue() : null;
    }

    /**
     * Flags an existing audit row for QR reprint (no new row) and resets the
     * replacement status. QR location is overwritten only when sent; audit
     * user / time are kept as audited.
     */
    public int markAuditQrReprint(
            long id,
            String auditMode,
            String qrFac,
            String qrFloor,
            String qrPositionA,
            String qrPositionAA) {
        String sql = """
                UPDATE F2Database.dbo.F2_FIXED_ASSET_AUDIT
                SET QrMismatch = 1,
                    ReprintSts = 0,
                    ReprintAt = NULL,
                    ReprintUserId = NULL,
                    AuditMode = COALESCE(?, AuditMode),
                    Fac_Qr = COALESCE(?, Fac_Qr),
                    Floor_Qr = COALESCE(?, Floor_Qr),
                    A_Qr = COALESCE(?, A_Qr),
                    AA_Qr = COALESCE(?, AA_Qr)
                WHERE Id = ?
                """;

        return jdbcTemplate.update(sql, auditMode, qrFac, qrFloor, qrPositionA, qrPositionAA, id);
    }

    /**
     * Marks the QR label of a flagged audit row as replaced (new label matches
     * its ACTUAL location). Only ReprintSts / ReprintAt / ReprintUserId change:
     * QrMismatch, *_Qr (audit-time label, kept as history), *_Act, Note,
     * AuditMode and audit user / time are kept.
     * photo3 (optional, "after/{file}"): Photo3 / Photo3_Old of the same row in
     * the same statement (see AUDIT_PHOTO3_SET).
     */
    public int clearAuditQrReprint(
            long id,
            LocalDateTime reprintAt,
            String reprintUserId,
            String photo3) {
        String sql = """
                UPDATE a
                SET ReprintSts = 1,
                    ReprintAt = ?,
                    ReprintUserId = ?"""
                + (photo3 == null ? "\n" : AUDIT_PHOTO3_SET)
                + """
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT a
                WHERE a.Id = ?
                """;

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(reprintAt));
        params.add(reprintUserId);
        addPhoto3Params(params, photo3);
        params.add(id);
        return jdbcTemplate.update(sql, params.toArray());
    }

    private void addPhoto3Params(List<Object> params, String photo3) {
        if (photo3 != null) {
            params.add(photo3);
            params.add(div);
            params.add(factory);
        }
    }

    /**
     * Confirmed move of a flagged machine: overwrites ACTUAL and QR label
     * location, QrMismatch stays 1 and ReprintSts stays 0. Audit user / time
     * are kept. photo3 (optional): as clearAuditQrReprint.
     */
    public int relocateAuditQrReprint(
            long id,
            String fac,
            String floor,
            String positionA,
            String positionAA,
            String qrFac,
            String qrFloor,
            String qrPositionA,
            String qrPositionAA,
            String photo3) {
        String sql = """
                UPDATE a
                SET QrMismatch = 1,
                    ReprintSts = 0,
                    Fac_Act = ?,
                    Floor_Act = ?,
                    A_Act = ?,
                    AA_Act = ?,
                    Fac_Qr = ?,
                    Floor_Qr = ?,
                    A_Qr = ?,
                    AA_Qr = ?"""
                + (photo3 == null ? "\n" : AUDIT_PHOTO3_SET)
                + """
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT a
                WHERE a.Id = ?
                """;

        // Arrays.asList: location values may be null.
        List<Object> params = new ArrayList<>(Arrays.asList(
                fac,
                floor,
                positionA,
                positionAA,
                qrFac,
                qrFloor,
                qrPositionA,
                qrPositionAA));
        addPhoto3Params(params, photo3);
        params.add(id);
        return jdbcTemplate.update(sql, params.toArray());
    }

    /**
     * Machines whose latest audit in the period has QrMismatch = 1 and
     * ReprintSts = 0 (QR label still waiting for replacement).
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
                    a.Id,
                    LTRIM(RTRIM(a.MachineCode)) AS MachineCode,
                    m.FAName,
                    m.AssetDate,
                    a.Fac_Act,
                    a.Floor_Act,
                    a.A_Act,
                    a.AA_Act,
                    a.Fac_Qr,
                    a.Floor_Qr,
                    a.A_Qr,
                    a.AA_Qr,
                    a.UserName,
                    a.UpdatedAt,
                    a.AuditMode,
                    a.ReprintSts,
                    a.ReprintAt,
                    a.Pic
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT a
                OUTER APPLY (
                    SELECT TOP 1
                        fa.FAName,
                        TRY_CONVERT(date, fa.DateStart) AS AssetDate,
                        LTRIM(RTRIM(map.Fac)) AS Fac
                    FROM F2Database.dbo.F2_FIXED_ASSET fa
                    LEFT JOIN F2Database.dbo.F2_FIXED_ASSET_MAP map
                """
                + "        ON " + mapDivCondition + "\n"
                + """
                        AND fa.[Floor] = map.[Floor]
                        AND fa.PositionA = map.A
                        AND fa.PositionAA = map.AA
                """
                + "    WHERE " + SCOPE_CONDITION + "\n"
                + """
                        AND LTRIM(RTRIM(fa.MachineCode)) = LTRIM(RTRIM(a.MachineCode))
                    ORDER BY map.Fac DESC
                ) m
                WHERE a.QrMismatch = 1
                    AND ISNULL(a.ReprintSts, 0) = 0
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

        // OUTER APPLY (div, factory) comes before the WHERE params.
        List<Object> params = new ArrayList<>();
        params.add(div);
        params.add(factory);
        params.add(Timestamp.valueOf(periodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));
        if (filterFac) {
            params.add(fac.trim());
        }

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new FixedAssetQrMismatchDto(
                        resultSet.getLong("Id"),
                        resultSet.getString("MachineCode"),
                        resultSet.getDate("AssetDate") != null ? resultSet.getDate("AssetDate").toLocalDate() : null,
                        resultSet.getString("FAName"),
                        resultSet.getString("Fac_Act"),
                        resultSet.getString("Floor_Act"),
                        resultSet.getString("A_Act"),
                        resultSet.getString("AA_Act"),
                        resultSet.getString("Fac_Qr"),
                        resultSet.getString("Floor_Qr"),
                        resultSet.getString("A_Qr"),
                        resultSet.getString("AA_Qr"),
                        resultSet.getString("UserName"),
                        resultSet.getTimestamp("UpdatedAt").toLocalDateTime(),
                        resultSet.getString("AuditMode"),
                        resultSet.getBoolean("ReprintSts"),
                        toLocalDateTime(resultSet.getTimestamp("ReprintAt")),
                        resultSet.getString("Pic")),
                params.toArray());
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
