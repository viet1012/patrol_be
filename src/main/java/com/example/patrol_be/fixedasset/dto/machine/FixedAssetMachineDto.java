package com.example.patrol_be.fixedasset.dto.machine;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FixedAssetMachineDto {
    private String machineCode;
    private String faName;
    private boolean auditedInPeriod;
    /** Latest audit row in the period has QrMismatch = 1. */
    private boolean needsQrReprint;
    /** MASTER location "Fac/Floor/A/AA" (Fac empty when not mapped). */
    private String masterLocation;
    /** Effective location (latest audit actual in the period) differs from MASTER. */
    @JsonProperty("isMoved")
    private boolean isMoved;
}
