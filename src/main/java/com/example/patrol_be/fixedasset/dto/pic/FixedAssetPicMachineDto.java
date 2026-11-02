package com.example.patrol_be.fixedasset.dto.pic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One machine of a PIC (summary detail) at its effective location, with the
 * latest audit in the period (audit fields null when not audited).
 */
@Data
@AllArgsConstructor
public class FixedAssetPicMachineDto {
    private String machineCode;
    private String machineName;
    private String fac;
    private String floor;
    private String a;
    private String aa;
    private boolean audited;
    /** "MSNV_HoTen" (UserId + "_" + UserName of the latest audit). */
    private String auditUserName;
    private LocalDateTime auditedAt;
    private String auditMode;
    private boolean qrMismatch;
    /** QR label location "Fac/Floor/A/AA" of the latest audit; null when none. */
    private String qrLocation;
    /** ReprintSts of the latest audit; null when not audited. */
    private Boolean reprintSts;
    /** Effective location (latest audit actual in the period) differs from MASTER. */
    @JsonProperty("isMoved")
    private boolean isMoved;
    /** MASTER location "Fac/Floor/A/AA" (Fac empty when not mapped). */
    private String masterLocation;
    private String note;
}
