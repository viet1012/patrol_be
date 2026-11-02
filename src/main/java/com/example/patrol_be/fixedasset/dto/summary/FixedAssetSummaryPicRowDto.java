package com.example.patrol_be.fixedasset.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FixedAssetSummaryPicRowDto {
    /** Current PIC of the machines; null on the total row. */
    private String pic;
    /** Boxed so Lombok / Jackson expose it as "isTotal" (a boolean would clash with total). */
    private Boolean isTotal;
    private long total;
    private long audited;
    /** One entry per month of the period. */
    private List<FixedAssetSummaryMonthDto> months;
}
