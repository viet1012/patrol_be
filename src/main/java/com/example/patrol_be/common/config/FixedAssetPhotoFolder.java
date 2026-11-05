package com.example.patrol_be.common.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Sub folders of app.storage.fixed-asset-photo-dir (single source of truth):
 * audit/ = Kiểm kê photo (file only), before/ = Thay QR step 1, old QR scan
 * (file only), after/ = Thay QR step 2, audit Photo3.
 */
public enum FixedAssetPhotoFolder {
    AUDIT("audit"),
    BEFORE("before"),
    AFTER("after");

    private final String value;

    FixedAssetPhotoFolder(String value) {
        this.value = value;
    }

    /** Folder name on disk / in the URL / DB value prefix. */
    public String value() {
        return value;
    }

    /** "audit, before, after". */
    public static String allowedValues() {
        return Arrays.stream(values()).map(FixedAssetPhotoFolder::value).collect(Collectors.joining(", "));
    }

    /** Folder of a request parameter (trimmed, case-insensitive); IllegalArgumentException when unknown. */
    public static FixedAssetPhotoFolder fromParam(String param) {
        String key = param == null ? "" : param.trim().toLowerCase(Locale.ROOT);
        for (FixedAssetPhotoFolder folder : values()) {
            if (folder.value.equals(key)) {
                return folder;
            }
        }
        throw new IllegalArgumentException(
                "Invalid Fixed Asset photo folder: " + param + " (allowed: " + allowedValues() + ")");
    }

    @Override
    public String toString() {
        return value;
    }
}
