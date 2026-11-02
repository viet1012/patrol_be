package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FixedAssetZoneLockDto {
    private boolean locked;
    private String reason;
    private String fac;
    private String floor;
    private String positionA;
    private String positionAA;
    private Long total;
    private Long audited;
    private LocalDateTime lastAuditedAt;
}
