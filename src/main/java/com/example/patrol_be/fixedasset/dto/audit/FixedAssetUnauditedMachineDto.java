package com.example.patrol_be.fixedasset.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Data;

/** fac is null when the MASTER location has no MAP row. */
@Data
@AllArgsConstructor
public class FixedAssetUnauditedMachineDto {
    private String machineCode;
    private String faName;
    private String fac;
    private String floor;
    private String positionA;
    private String positionAA;
}
