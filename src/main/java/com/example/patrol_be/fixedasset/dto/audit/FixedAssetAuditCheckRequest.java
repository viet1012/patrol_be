package com.example.patrol_be.fixedasset.dto;

import lombok.Data;

@Data
public class FixedAssetAuditCheckRequest {
    private String div;
    private String machineCode;
    private String floor;
    private String positionAA;
}
