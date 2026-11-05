package com.example.patrol_be.fixedasset.service;

import com.example.patrol_be.common.config.FixedAssetPhotoFolder;
import com.example.patrol_be.common.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
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
 * app.storage.fixed-asset-photo-dir/{audit|before|after}: folder creation, file
 * name {qr}_3.jpg (fallback from MASTER), same-name handling, path traversal
 * guard and content type / size check. DB value = "{folder}/{file}" (served at /images/FixedAsset/{folder}/{file}).
 */
@Slf4j
@Component
public class FixedAssetPhotoStorage {
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    // Windows-invalid file name characters and control characters.
    private static final String INVALID_FILE_NAME_CHARS = "[\\\\/:*?\"<>|\\p{Cntrl}]";
    public static final String EXTENSION = ".jpg";
    private static final String PHOTO_SUFFIX = "_3";

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
    public Path folder(FixedAssetPhotoFolder folder) throws IOException {
        Path dir = storage.fixedAssetPhotoPath(folder);
        if (dir == null) {
            return null;
        }
        Files.createDirectories(dir);
        return Files.isWritable(dir) ? dir : null;
    }

    /**
     * Photo file name of every folder (audit/, before/, after/): "{qr}_3.jpg" when
     * the cleaned QR content contains machineCode (case-insensitive), else the
     * fallback "{Div}_{MachineCode}_{Floor}_{PositionAA}_{Group}_3.jpg".
     * Both cleaned by {@link #cleanFileName}.
     */
    public String photoFileName(String qr, String machineCode,
                                String div, String floor, String positionAA, String group) {
        String code = trimToEmpty(machineCode);
        String qrText = stripControl(qr);
        boolean useQr = !qrText.isEmpty() && !code.isEmpty()
                && qrText.toUpperCase(Locale.ROOT).contains(code.toUpperCase(Locale.ROOT));
        String base = useQr
                ? qrText
                : String.join("_",
                        trimToEmpty(div), code, trimToEmpty(floor), trimToEmpty(positionAA), trimToEmpty(group));
        return cleanFileName(base) + PHOTO_SUFFIX + EXTENSION;
    }

    /** Control characters removed, trimmed, \ / : * ? " < > | -> "-", spaces kept. */
    public String cleanFileName(String value) {
        return stripControl(value).replaceAll(INVALID_FILE_NAME_CHARS, "-");
    }

    /**
     * Same-name handling: when target exists it is renamed to
     * {name}_{yyyyMMdd_HHmmss}.jpg (then _1, _2, ... if that is taken too).
     * Returns the renamed path, null when target did not exist.
     */
    public Path moveExistingAside(Path target) throws IOException {
        if (!Files.exists(target)) {
            return null;
        }
        Path dir = target.getParent();
        String fileName = target.getFileName().toString();
        String base = fileName.endsWith(EXTENSION)
                ? fileName.substring(0, fileName.length() - EXTENSION.length())
                : fileName;
        String stamped = base + "_" + LocalDateTime.now().format(TIME_FORMAT);
        Path renamed = resolve(dir, stamped + EXTENSION);
        for (int i = 1; Files.exists(renamed); i++) {
            renamed = resolve(dir, stamped + "_" + i + EXTENSION);
        }
        // Fails (no overwrite) if another request took that name meanwhile.
        Files.move(target, renamed);
        return renamed;
    }

    /** Undo of moveExistingAside: renamed back to target (logged when it fails). */
    public void restoreAside(Path renamed, Path target) {
        if (renamed == null) {
            return;
        }
        try {
            Files.move(renamed, target);
        } catch (IOException exception) {
            log.warn("Cannot rename Fixed Asset photo {} back to {}", renamed, target, exception);
        }
    }

    private static String stripControl(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", "").trim();
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
    public String dbValue(FixedAssetPhotoFolder folder, Path file) {
        return folder.value() + "/" + file.getFileName();
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
