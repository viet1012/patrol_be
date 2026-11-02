package com.example.patrol_be.fixedasset.dto.pic;

import com.example.patrol_be.fixedasset.dto.audit.FixedAssetUnauditedMachineDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** Same fields as {@link FixedAssetPicSummaryDto} + zones + unaudited machines. */
@Data
@AllArgsConstructor
public class FixedAssetPicDetailDto {
    private String pic;
    private String name;
    private long total;
    private long audited;
    private double pct;
    private long remaining;
    private long zoneCount;
    private long pendingQr;
    private double speedPerWeek;
    private LocalDate etaDate;
    private String status;
    private List<FixedAssetPicZoneDto> zones;
    private List<FixedAssetUnauditedMachineDto> unaudited;
}
