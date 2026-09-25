package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FixedAssetScanInfoDto {
    private String machineCode;
    private String div;
    /** MachineCode exists in F2_FIXED_ASSET (direct lookup, no MAP join). */
    private boolean existsInMaster;
    /** MASTER Floor / PositionA / PositionAA are all present in F2_FIXED_ASSET. */
    private boolean masterLocationResolved;
    /** The exact MASTER location resolves to one Fac in MAP; false -> fac is null. */
    private boolean masterMappingValid;
    private String fac;
    private String floor;
    private String positionA;
    private String positionAA;
    private String faName;
    private boolean auditedInCurrentPeriod;
    private LocalDateTime lastAuditedAt;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}
