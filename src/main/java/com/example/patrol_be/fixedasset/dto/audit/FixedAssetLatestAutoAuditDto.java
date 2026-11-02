package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FixedAssetLatestAutoAuditDto {
    private String positionA;
    private String positionAA;
    private LocalDateTime updatedAt;
}
