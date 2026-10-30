package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FixedAssetLatestAuditDto {
    private long id;
    private String userId;
    private String userName;
    private LocalDateTime updatedAt;
    /** QrMismatch = 1 AND ReprintSts = 0: still audited, QR label waiting for replacement. */
    private boolean needsQrReprint;
    /** ReprintSts = 1: QR label replaced ("3) Thay QR"). */
    private boolean reprintSts;
    private LocalDateTime reprintAt;
    /** ACTUAL location of the row (*_Act). */
    private String facAct;
    private String floorAct;
    private String positionAAct;
    private String positionAAAct;
    /** QR label location of the row (*_Qr); all null for a NO_QR row. */
    private String qrFac;
    private String qrFloor;
    private String qrPositionA;
    private String qrPositionAA;
}
