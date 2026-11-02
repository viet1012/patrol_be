package com.example.patrol_be.fixedasset.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
public class FixedAssetSummaryDto {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate today;
    private double planPct;
    private long total;
    private long audited;
    private double pct;
    private long remaining;
    /** Machines waiting for QR replacement (needsQrReprint). */
    private long pendingQr;
    /** PICs (null PIC excluded) with pendingQr > 0. */
    private long pendingQrPics;
    /** PICs, null PIC ("Chưa có PIC") excluded. */
    private long picCount;
    /** PICs (null PIC excluded) with status ON_TRACK. */
    private long onTrackPicCount;
    /** Total row (isTotal, all machines) first, then one row per PIC, then machines without PIC (pic = null, isTotal = false) when any. */
    private List<FixedAssetSummaryPicRowDto> picRows;
}
