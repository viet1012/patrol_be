package com.example.patrol_be.fixedasset.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One MASTER machine × MASTER location (summary source). fac is null when the
 * location has no MAP row; a machine with several MASTER locations has several rows.
 */
@Data
@AllArgsConstructor
public class FixedAssetSummaryMachineRowDto {
    private String machineCode;
    private String faName;
    /** Per machine (same for all its rows): MAX normalized EmailChecked, null when blank. */
    private String pic;
    private String fac;
    private String floor;
    private String positionA;
    private String positionAA;
    /** First audit row of the machine in the period; null = not audited. */
    private LocalDateTime firstAuditAt;
    /** Latest audit row exists in the period (same rule as /audit-summary). */
    private boolean auditedInPeriod;
    private boolean needsQrReprint;
    /** Effective location differs from MASTER (as findMachines isMoved). */
    private boolean moved;
}
