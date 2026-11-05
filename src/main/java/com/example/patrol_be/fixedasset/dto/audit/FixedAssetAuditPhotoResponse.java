package com.example.patrol_be.fixedasset.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Result of POST /audits/{id}/photo (file only, nothing saved in DB). */
@Data
@AllArgsConstructor
public class FixedAssetAuditPhotoResponse {
    private long auditId;
    /** Saved file under fixed-asset-photo-dir: "audit/{file}" or "before/{file}". */
    private String photoPath;
    /** Full URL via image-base-url; null when image-base-url is not set. */
    private String photoUrl;
}
