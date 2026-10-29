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
    /** Latest row has QrMismatch = 1: still audited, QR label must be reprinted. */
    private boolean needsQrReprint;
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
