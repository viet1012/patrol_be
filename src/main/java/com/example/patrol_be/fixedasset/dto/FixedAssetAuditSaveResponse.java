package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FixedAssetAuditSaveResponse {
    private boolean success;
    private boolean saved;
    private boolean alreadyAudited;
    /** Saved / existing row has QrMismatch = 1 (QR label must be reprinted). */
    private boolean needsQrReprint;
    private boolean unknownMachine;
    private boolean locationMismatch;
    private boolean requiresConfirmation;
    private String machineCode;
    private LocalDateTime updatedAt;
    private LocalDateTime lastAuditedAt;
    private String lastAuditedUserId;
    private String lastAuditedUserName;
    private String masterFac;
    private String masterFloor;
    private String masterPositionA;
    private String masterPositionAA;
    private String actualFac;
    private String actualFloor;
    private String actualPositionA;
    private String actualPositionAA;
    private String message;
    /** Flagged machine rescanned with its old (not reprinted) QR label. */
    private boolean qrLabelNotReplaced;
    /** QR label location of the audit row after this save; null when it no longer needs reprint. */
    private String reprintQrFac;
    private String reprintQrFloor;
    private String reprintQrPositionA;
    private String reprintQrPositionAA;
}
