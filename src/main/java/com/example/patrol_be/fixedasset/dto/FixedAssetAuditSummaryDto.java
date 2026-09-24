package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class FixedAssetAuditSummaryDto {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private long totalMachines;
    private long auditedMachines;
    private long remainingMachines;
    private double completionPercent;
}
