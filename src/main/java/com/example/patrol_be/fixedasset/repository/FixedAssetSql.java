package com.example.patrol_be.fixedasset.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQL fragments and param helpers shared by the fixed-asset repositories. */
final class FixedAssetSql {
    /**
     * Latest audit row per machine in the period (UpdatedAt DESC, Id DESC).
     * Params: periodStart, nextPeriodStart. Any row in the period counts as
     * audited; QrMismatch + ReprintSts only drive needsQrReprint (label status).
     * *_Act (trimmed) give the effective location (see effectiveMachineSource).
     */
    static final String LATEST_AUDIT_IN_PERIOD = """
            (
                SELECT
                    ranked.MachineCode, ranked.QrMismatch, ranked.ReprintSts,
                    ranked.Fac_Act, ranked.Floor_Act, ranked.A_Act, ranked.AA_Act,
                    ranked.Fac_Qr, ranked.Floor_Qr, ranked.A_Qr, ranked.AA_Qr,
                    ranked.UserId, ranked.UserName, ranked.UpdatedAt, ranked.AuditMode, ranked.Note
                FROM (
                    SELECT
                        LTRIM(RTRIM(a.MachineCode)) AS MachineCode,
                        a.QrMismatch,
                        a.ReprintSts,
                        LTRIM(RTRIM(a.Fac_Act)) AS Fac_Act,
                        LTRIM(RTRIM(a.Floor_Act)) AS Floor_Act,
                        LTRIM(RTRIM(a.A_Act)) AS A_Act,
                        LTRIM(RTRIM(a.AA_Act)) AS AA_Act,
                        LTRIM(RTRIM(a.Fac_Qr)) AS Fac_Qr,
                        LTRIM(RTRIM(a.Floor_Qr)) AS Floor_Qr,
                        LTRIM(RTRIM(a.A_Qr)) AS A_Qr,
                        LTRIM(RTRIM(a.AA_Qr)) AS AA_Qr,
                        LTRIM(RTRIM(a.UserId)) AS UserId,
                        LTRIM(RTRIM(a.UserName)) AS UserName,
                        a.UpdatedAt,
                        a.AuditMode,
                        a.Note,
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

    static final String AUDITED_CONDITION = "latest.MachineCode IS NOT NULL";
    /** Waiting for QR replacement: QrMismatch = 1 AND ReprintSts = 0 (NULL treated as 0). */
    static final String NEEDS_QR_REPRINT_CONDITION = "latest.MachineCode IS NOT NULL"
            + " AND ISNULL(latest.QrMismatch, 0) = 1 AND ISNULL(latest.ReprintSts, 0) = 0";
    /** Latest audit in the period has a full actual location (all 4 levels non-blank). */
    static final String HAS_ACTUAL_LOCATION_CONDITION = "latest.MachineCode IS NOT NULL"
            + " AND ISNULL(latest.Fac_Act, '') <> '' AND ISNULL(latest.Floor_Act, '') <> ''"
            + " AND ISNULL(latest.A_Act, '') <> '' AND ISNULL(latest.AA_Act, '') <> ''";

    /**
     * Machine scope of the module (2 params: div, factory):
     * Div = fixed-asset.div OR Factory = fixed-asset.factory.
     */
    static final String SCOPE_CONDITION =
            "(LTRIM(RTRIM(fa.Div)) = ? OR LTRIM(RTRIM(fa.Factory)) = ?)";
    /** PIC of a machine: EmailChecked, trimmed + lower-cased. */
    static final String PIC_EXPR = "LOWER(LTRIM(RTRIM(fa.EmailChecked)))";

    /**
     * Current PIC per MASTER machine in scope (non-blank PIC_EXPR, MAX over
     * MASTER duplicates; null when none). Params: div, factory.
     */
    static final String MACHINE_PIC = """
            (
                SELECT
                    LTRIM(RTRIM(fa.MachineCode)) AS MachineCode,
            """
            + "        MAX(NULLIF(" + PIC_EXPR + ", '')) AS Pic\n"
            + "    FROM F2Database.dbo.F2_FIXED_ASSET fa\n"
            + "    WHERE " + SCOPE_CONDITION + "\n"
            + """
                    AND fa.MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(fa.MachineCode)) <> ''
                GROUP BY LTRIM(RTRIM(fa.MachineCode))
            )""";

    /** First audit time per machine in the period. Params: periodStart, nextPeriodStart. */
    static final String FIRST_AUDIT_IN_PERIOD = """
            (
                SELECT
                    LTRIM(RTRIM(a.MachineCode)) AS MachineCode,
                    MIN(a.UpdatedAt) AS FirstAuditAt
                FROM F2Database.dbo.F2_FIXED_ASSET_AUDIT a
                WHERE a.MachineCode IS NOT NULL
                    AND LTRIM(RTRIM(a.MachineCode)) <> ''
                    AND a.UpdatedAt >= ?
                    AND a.UpdatedAt < ?
                GROUP BY LTRIM(RTRIM(a.MachineCode))
            )""";

    private FixedAssetSql() {
    }

    /**
     * MAP rows of fixed-asset.div (already trimmed), shared by every machine in
     * scope (MAP has no Factory column). Inlined as an escaped literal: the
     * join sits before the other params, so a "?" would shift every query's params.
     */
    static String mapDivCondition(String div) {
        return "LTRIM(RTRIM(map.Div)) = '" + div.replace("'", "''") + "'";
    }

    /** "AND <pic condition>" when pic is given (1 param), else empty: same result as before. */
    static String picFilter(String pic) {
        return pic == null ? "" : "    AND " + PIC_EXPR + " = ?\n";
    }

    /** Scope params (div, factory), then pic param when given. */
    static List<Object> scopeParams(String div, String factory, String pic) {
        List<Object> params = new ArrayList<>();
        params.add(div);
        params.add(factory);
        if (pic != null) {
            params.add(pic);
        }
        return params;
    }

    /** Actual column of the latest audit when it has a full actual location, else the MASTER column. */
    private static String effectiveColumn(String actColumn, String masterColumn) {
        return "CASE WHEN " + HAS_ACTUAL_LOCATION_CONDITION
                + " THEN latest." + actColumn + " ELSE m." + masterColumn + " END";
    }

    /**
     * Every MASTER machine in scope × its MASTER location (LEFT JOIN MAP -> Fac
     * null when not mapped) with its effective location: actual location of the
     * latest audit in the period, else MASTER. Read-only, MASTER is not updated.
     * Columns (trimmed): MachineCode, FAName, Pic (as PIC_EXPR), MasterFac / MasterFloor /
     * MasterPositionA / MasterPositionAA, Fac / Floor / PositionA / PositionAA
     * (effective), AuditedInPeriod, NeedsQrReprint, Moved (0/1), and latest audit
     * fields (null when not audited): AuditUserId, AuditUserName, AuditedAt,
     * AuditMode, AuditQrMismatch / AuditReprintSts (int), Fac_Qr / Floor_Qr /
     * A_Qr / AA_Qr, AuditNote.
     * Params: see {@link #effectiveMachineParams}.
     */
    static String effectiveMachineSource(String mapDivCondition, String pic) {
        return """
                (
                    SELECT
                        m.MachineCode,
                        m.FAName,
                        m.Pic,
                        m.Fac AS MasterFac,
                        m.Floor AS MasterFloor,
                        m.PositionA AS MasterPositionA,
                        m.PositionAA AS MasterPositionAA,
                """
                + "        " + effectiveColumn("Fac_Act", "Fac") + " AS Fac,\n"
                + "        " + effectiveColumn("Floor_Act", "Floor") + " AS Floor,\n"
                + "        " + effectiveColumn("A_Act", "PositionA") + " AS PositionA,\n"
                + "        " + effectiveColumn("AA_Act", "PositionAA") + " AS PositionAA,\n"
                + "        CASE WHEN " + AUDITED_CONDITION + " THEN 1 ELSE 0 END AS AuditedInPeriod,\n"
                + "        CASE WHEN " + NEEDS_QR_REPRINT_CONDITION + " THEN 1 ELSE 0 END AS NeedsQrReprint,\n"
                + "        CASE WHEN " + HAS_ACTUAL_LOCATION_CONDITION + """
                 AND (
                                ISNULL(m.Fac, '') <> latest.Fac_Act
                                OR ISNULL(m.Floor, '') <> latest.Floor_Act
                                OR ISNULL(m.PositionA, '') <> latest.A_Act
                                OR ISNULL(m.PositionAA, '') <> latest.AA_Act
                            ) THEN 1 ELSE 0 END AS Moved,
                        latest.UserId AS AuditUserId,
                        latest.UserName AS AuditUserName,
                        latest.UpdatedAt AS AuditedAt,
                        latest.AuditMode,
                        CAST(latest.QrMismatch AS int) AS AuditQrMismatch,
                        CAST(latest.ReprintSts AS int) AS AuditReprintSts,
                        latest.Fac_Qr,
                        latest.Floor_Qr,
                        latest.A_Qr,
                        latest.AA_Qr,
                        latest.Note AS AuditNote
                    FROM (
                        SELECT DISTINCT
                            LTRIM(RTRIM(fa.MachineCode)) AS MachineCode,
                            fa.FAName,
                """
                + "            " + PIC_EXPR + " AS Pic,\n"
                + """
                            LTRIM(RTRIM(map.Fac)) AS Fac,
                            LTRIM(RTRIM(fa.[Floor])) AS Floor,
                            LTRIM(RTRIM(fa.PositionA)) AS PositionA,
                            LTRIM(RTRIM(fa.PositionAA)) AS PositionAA
                        FROM F2Database.dbo.F2_FIXED_ASSET fa
                        LEFT JOIN F2Database.dbo.F2_FIXED_ASSET_MAP map
                """
                + "            ON " + mapDivCondition + "\n"
                + """
                            AND fa.[Floor] = map.[Floor]
                            AND fa.PositionA = map.A
                            AND fa.PositionAA = map.AA
                """
                + "        WHERE " + SCOPE_CONDITION + "\n"
                + picFilter(pic)
                + """
                            AND fa.MachineCode IS NOT NULL
                            AND LTRIM(RTRIM(fa.MachineCode)) <> ''
                    ) m
                """
                + "    LEFT JOIN " + LATEST_AUDIT_IN_PERIOD + " latest\n"
                + "        ON latest.MachineCode = m.MachineCode\n"
                + ") eff\n";
    }

    /** Params of {@link #effectiveMachineSource}: div, factory, pic (when given), periodStart, nextPeriodStart. */
    static List<Object> effectiveMachineParams(
            String div,
            String factory,
            String pic,
            LocalDateTime periodStart,
            LocalDateTime nextPeriodStart) {
        List<Object> params = scopeParams(div, factory, pic);
        params.add(Timestamp.valueOf(periodStart));
        params.add(Timestamp.valueOf(nextPeriodStart));
        return params;
    }

    static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
