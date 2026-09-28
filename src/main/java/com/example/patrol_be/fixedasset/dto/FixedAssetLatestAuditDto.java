package com.example.patrol_be.fixedasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FixedAssetLatestAuditDto {
    private String userId;
    private String userName;
    private LocalDateTime updatedAt;
}
