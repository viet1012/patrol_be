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
    private boolean unknownMachine;
    private boolean locationMismatch;
    private boolean requiresConfirmation;
    private String machineCode;
    private LocalDateTime updatedAt;
    private LocalDateTime lastAuditedAt;
    private String masterFac;
    private String masterFloor;
    private String masterPositionA;
    private String masterPositionAA;
    private String actualFac;
    private String actualFloor;
    private String actualPositionA;
    private String actualPositionAA;
    private String message;
}
