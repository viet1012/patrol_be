package com.example.patrol_be.fixedasset.dto.pic;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class FixedAssetPicSummaryDto {
    /** Normalized EmailChecked; null = machines without PIC. */
    private String pic;
    private String name;
    private long total;
    private long audited;
    private double pct;
    private long remaining;
    /** Distinct mapped Fac / Floor / A / AA of the PIC's machines. */
    private long zoneCount;
    private long pendingQr;
    /** Audited machines per week over the last 4 weeks of the period (up to today). */
    private double speedPerWeek;
    /** null when speedPerWeek = 0 or remaining = 0. */
    private LocalDate etaDate;
    /** NOT_STARTED (audited = 0) / BEHIND (pct < planPct) / ON_TRACK. */
    private String status;
}
