package com.example.patrol_be.fixedasset.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Audit row columns used by the before photo upload (file name, permission). */
@Data
@AllArgsConstructor
public class FixedAssetAuditPhotoTargetDto {
    private long id;
    private String machineCode;
    /** Creator of the audit row. */
    private String userId;
}
