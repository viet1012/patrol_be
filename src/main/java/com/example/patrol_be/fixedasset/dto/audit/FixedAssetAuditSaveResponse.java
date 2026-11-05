package com.example.patrol_be.fixedasset.dto.audit;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FixedAssetAuditSaveResponse {
    private boolean success;
    private boolean saved;
    private boolean alreadyAudited;
    /** Saved / existing row has QrMismatch = 1 AND ReprintSts = 0 (QR label must be replaced). */
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
    /** Flagged machine scanned outside "3) Thay QR" (qrReplace != true): nothing saved. */
    private boolean pendingQrReplace;
    /** Row state after this save: ReprintSts = 1 (QR replaced) and when. */
    private boolean reprintSts;
    private LocalDateTime reprintAt;
    /** "3) Thay QR" photo written and audit Photo3 updated by this save. */
    private boolean photoSaved;
    /** New audit Photo3 (file name in after/); null when no photo was saved. */
    private String photo3;
    /** {image-base-url}FixedAsset/after/{photo3}; null when no photo / base URL not set. */
    private String photo3Url;
    /** Id of the saved / existing audit row; null when no row (confirmation needed). */
    private Long auditId;

    // Photo fields and auditId are set after construction (only the QR replace flow saves a photo).
    public FixedAssetAuditSaveResponse(
            boolean success,
            boolean saved,
            boolean alreadyAudited,
            boolean needsQrReprint,
            boolean unknownMachine,
            boolean locationMismatch,
            boolean requiresConfirmation,
            String machineCode,
            LocalDateTime updatedAt,
            LocalDateTime lastAuditedAt,
            String lastAuditedUserId,
            String lastAuditedUserName,
            String masterFac,
            String masterFloor,
            String masterPositionA,
            String masterPositionAA,
            String actualFac,
            String actualFloor,
            String actualPositionA,
            String actualPositionAA,
            String message,
            boolean qrLabelNotReplaced,
            String reprintQrFac,
            String reprintQrFloor,
            String reprintQrPositionA,
            String reprintQrPositionAA,
            boolean pendingQrReplace,
            boolean reprintSts,
            LocalDateTime reprintAt
    ) {
        this.success = success;
        this.saved = saved;
        this.alreadyAudited = alreadyAudited;
        this.needsQrReprint = needsQrReprint;
        this.unknownMachine = unknownMachine;
        this.locationMismatch = locationMismatch;
        this.requiresConfirmation = requiresConfirmation;
        this.machineCode = machineCode;
        this.updatedAt = updatedAt;
        this.lastAuditedAt = lastAuditedAt;
        this.lastAuditedUserId = lastAuditedUserId;
        this.lastAuditedUserName = lastAuditedUserName;
        this.masterFac = masterFac;
        this.masterFloor = masterFloor;
        this.masterPositionA = masterPositionA;
        this.masterPositionAA = masterPositionAA;
        this.actualFac = actualFac;
        this.actualFloor = actualFloor;
        this.actualPositionA = actualPositionA;
        this.actualPositionAA = actualPositionAA;
        this.message = message;
        this.qrLabelNotReplaced = qrLabelNotReplaced;
        this.reprintQrFac = reprintQrFac;
        this.reprintQrFloor = reprintQrFloor;
        this.reprintQrPositionA = reprintQrPositionA;
        this.reprintQrPositionAA = reprintQrPositionAA;
        this.pendingQrReplace = pendingQrReplace;
        this.reprintSts = reprintSts;
        this.reprintAt = reprintAt;
    }
}
