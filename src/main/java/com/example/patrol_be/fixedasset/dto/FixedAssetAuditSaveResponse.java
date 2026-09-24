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
    private String machineCode;
    private LocalDateTime updatedAt;
    private LocalDateTime lastAuditedAt;
    private String message;
}
