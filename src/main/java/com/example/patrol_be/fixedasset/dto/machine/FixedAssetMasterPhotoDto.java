package com.example.patrol_be.fixedasset.dto.machine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Photo columns of one F2_FIXED_ASSET row (Div / Group feed the Photo3 file name). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FixedAssetMasterPhotoDto {
    private String div;
    private String machineCode;
    private String group;
    /** File name under app.storage.fixed-asset-photo-dir. */
    private String photo3;
    /** Previous Photo3, kept when Photo3 is replaced. */
    private String photo3Old;
}
