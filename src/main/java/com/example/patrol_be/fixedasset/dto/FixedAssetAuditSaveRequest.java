package com.example.patrol_be.fixedasset.dto;

import lombok.Data;

@Data
public class FixedAssetAuditSaveRequest {
    private String div;
    private String fac;
    private String floor;
    private String positionA;
    private String positionAA;
    private String machineCode;
    private String userId;
    private String userName;
    private String note;
    private Boolean confirmLocationMismatch;
    private String mode;
    // Optional (Manual mode): location printed on the QR label.
    private String qrFloor;
    private String qrPositionA;
    private String qrPositionAA;
    private Boolean qrLocationMismatch;
}
