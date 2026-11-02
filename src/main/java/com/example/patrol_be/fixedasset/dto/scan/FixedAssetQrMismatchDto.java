package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FixedAssetQrMismatchDto {
    private String machineCode;
    private LocalDate assetDate; // import java.time.LocalDate;
    private String faName;
    private String facAct;
    private String floorAct;
    private String positionAAct;
    private String positionAAAct;
    private String qrFac;
    private String qrFloor;
    private String qrPositionA;
    private String qrPositionAA;
    private String userName;
    private LocalDateTime updatedAt;
    private String auditMode;
    private boolean reprintSts;
    private LocalDateTime reprintAt;
}
