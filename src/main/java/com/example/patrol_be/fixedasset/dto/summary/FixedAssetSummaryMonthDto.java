package com.example.patrol_be.fixedasset.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FixedAssetSummaryMonthDto {
    /** Calendar month (1-12). */
    private int month;
    /** Machines whose first audit in the period falls in this month. */
    private long audited;
}
