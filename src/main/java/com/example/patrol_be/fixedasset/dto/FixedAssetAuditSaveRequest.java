package com.example.patrol_be.fixedasset.dto;

import lombok.Data;

@Data
public class FixedAssetAuditSaveRequest {
    private String fac;
    private String floor;
    private String positionA;
    private String positionAA;
    private String machineCode;
    private String userId;
    private String userName;
    private String note;
}
