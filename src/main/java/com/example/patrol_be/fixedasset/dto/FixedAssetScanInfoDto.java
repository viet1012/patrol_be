package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FixedAssetScanInfoDto {
    private String machineCode;
    private boolean existsInMaster;
    private String fac;
    private String floor;
    private String positionA;
    private String positionAA;
    private String faName;
    private boolean auditedInCurrentPeriod;
    private LocalDateTime lastAuditedAt;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}
