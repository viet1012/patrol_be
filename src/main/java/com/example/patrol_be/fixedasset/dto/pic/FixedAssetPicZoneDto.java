package com.example.patrol_be.fixedasset.dto.pic;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FixedAssetPicZoneDto {
    private String fac;
    private String floor;
    private String positionA;
    private String positionAA;
    private long total;
    private long audited;
    /** Machines of the zone whose effective location differs from MASTER (isMoved). */
    private long movedCount;
}
