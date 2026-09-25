package com.example.patrol_be.fixedasset.dto;

import lombok.Data;

/**
 * One logical MASTER record read directly from F2_FIXED_ASSET (no MAP join).
 * Floor / PositionA / PositionAA are MASTER values; Fac comes from its optional MAP row.
 */
@Data
public class FixedAssetMasterMachineDto {
    private String fac;
    private String div;
    private String machineCode;
    private String floor;
    private String positionA;
    private String positionAA;
    private String faName;

    public FixedAssetMasterMachineDto() {
    }

    public FixedAssetMasterMachineDto(
            String fac,
            String div,
            String machineCode,
            String floor,
            String positionA,
            String positionAA,
            String faName
    ) {
        this.fac = fac;
        this.div = div;
        this.machineCode = machineCode;
        this.floor = floor;
        this.positionA = positionA;
        this.positionAA = positionAA;
        this.faName = faName;
    }
}
