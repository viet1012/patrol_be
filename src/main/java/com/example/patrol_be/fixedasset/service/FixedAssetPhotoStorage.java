package com.example.patrol_be.fixedasset.service;

import com.example.patrol_be.common.config.StorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * Shared file handling of Fixed Asset photos under
 * app.storage.fixed-asset-photo-dir/{before|after}: folder creation, name
 * {MachineCode}_{yyyyMMdd_HHmmss}, path traversal guard and content type /
 * size check. DB value = "{folder}/{file}" (served at /images/FixedAsset/{folder}/{file}).
 */
@Component
public class FixedAssetPhotoStorage {
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final Set<String> FOLDERS =
            Set.of(StorageProperties.FIXED_ASSET_BEFORE, StorageProperties.FIXED_ASSET_AFTER);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    // Windows-invalid file name characters and control characters.
    private static final String INVALID_FILE_NAME_CHARS = "[\\\\/:*?\"<>|\\p{Cntrl}]";

    private final StorageProperties storage;

    public FixedAssetPhotoStorage(StorageProperties storage) {
        this.storage = storage;
    }

    /** IllegalArgumentException when the content type is not allowed or the file is over 5MB. */
    public void validate(MultipartFile photo, Set<String> allowedTypes) {
        if (!allowedTypes.contains(contentType(photo))) {
            throw new IllegalArgumentException("Photo must be " + String.join(" / ", allowedTypes));
        }
        if (photo.getSize() > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("Photo must be at most 5MB");
        }
    }

    /**
     * fixed-asset-photo-dir/{folder}, created when missing; null when the root
     * is not set or the folder is not writable.
     */
    public Path folder(String folder) throws IOException {
        if (!FOLDERS.contains(folder)) {
            throw new IllegalArgumentException("Invalid Fixed Asset photo folder: " + folder);
        }
        Path dir = storage.fixedAssetPhotoPath(folder);
        if (dir == null) {
            return null;
        }
        Files.createDirectories(dir);
        return Files.isWritable(dir) ? dir : null;
    }

    /** {MachineCode}_{yyyyMMdd_HHmmss} (no extension), invalid file name characters -> "_". */
    public String baseName(String machineCode, LocalDateTime time) {
        String name = trimToEmpty(machineCode) + "_" + time.format(TIME_FORMAT);
        return name.replaceAll(INVALID_FILE_NAME_CHARS, "_");
    }

    /**
     * MASTER style Photo3 name (no extension): {Div}_{MachineCode}_{Floor}_{AA}_{Group}_3,
     * file name invalid characters removed, spaces kept.
     */
    public String photo3BaseName(String div, String machineCode, String floor, String positionAA, String group) {
        String name = String.join("_",
                trimToEmpty(div),
                trimToEmpty(machineCode),
                trimToEmpty(floor),
                trimToEmpty(positionAA),
                trimToEmpty(group),
                "3");
        return name.replaceAll(INVALID_FILE_NAME_CHARS, "");
    }

    /** dir/fileName, rejecting names that leave dir (../, sub folders). */
    public Path resolve(Path dir, String fileName) {
        Path file = dir.resolve(fileName).normalize();
        if (!dir.equals(file.getParent())) {
            throw new IllegalArgumentException("Invalid Fixed Asset photo file name: " + fileName);
        }
        return file;
    }

    /** Writes a new file (never overwrites an existing one). */
    public void write(MultipartFile photo, Path target) throws IOException {
        try (InputStream in = photo.getInputStream()) {
            Files.copy(in, target);
        }
    }

    /** DB value "{folder}/{file}". */
    public String dbValue(String folder, Path file) {
        return folder + "/" + file.getFileName();
    }

    public String url(String dbValue) {
        return storage.fixedAssetPhotoUrl(dbValue);
    }

    /** URL of an audit Photo3 (file name in after/). */
    public String afterUrl(String fileName) {
        return storage.fixedAssetAfterPhotoUrl(fileName);
    }

    private static String contentType(MultipartFile photo) {
        String type = photo.getContentType();
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
