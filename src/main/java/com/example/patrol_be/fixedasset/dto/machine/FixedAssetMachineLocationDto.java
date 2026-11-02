package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FixedAssetMachineLocationDto {
    private String fac;
    private String floor;
    private String positionA;
    private String positionAA;
    private String machineCode;
    private String faName;
}
