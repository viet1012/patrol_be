package com.example.patrol_be.fixedasset.dto.pic;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FixedAssetPicDto {
    /** F2_FIXED_ASSET.EmailChecked, trimmed + lower-cased. */
    private String email;
    /** Display name: part of the email before '@' (HSE_EmpID has no email column). */
    private String name;
    private long machineCount;
    /** Distinct Fac / Floor / A / AA of the PIC's mapped machines. */
    private long zoneCount;
}
