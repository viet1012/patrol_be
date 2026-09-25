package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FixedAssetAuditCheckResponse {
    private String machineCode;
    private boolean alreadyAudited;
    private LocalDateTime lastAuditedAt;
    /** MachineCode exists in F2_FIXED_ASSET (direct lookup, no MAP join). */
    private boolean existsInMaster;
    /** MASTER Floor / PositionA / PositionAA are all present in F2_FIXED_ASSET. */
    private boolean masterLocationResolved;
    /** The exact MASTER location also resolves to one Fac in F2_FIXED_ASSET_MAP. */
    private boolean masterMappingValid;
    private boolean actualLocationResolved;
    private boolean locationMatch;
    private boolean requiresConfirmation;
    private String masterFac;
    private String masterFloor;
    private String masterPositionA;
    private String masterPositionAA;
    private String faName;
    private String actualFac;
    private String actualFloor;
    private String actualPositionA;
    private String actualPositionAA;
    private String message;
}
