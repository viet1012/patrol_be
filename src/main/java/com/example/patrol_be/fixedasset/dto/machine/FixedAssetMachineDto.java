package com.example.patrol_be.fixedasset.dto;

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
}
