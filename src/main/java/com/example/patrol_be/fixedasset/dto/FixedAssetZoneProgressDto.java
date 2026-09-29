package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FixedAssetZoneProgressDto {
    private String positionA;
    private String positionAA;
    private long total;
    private long audited;
}
