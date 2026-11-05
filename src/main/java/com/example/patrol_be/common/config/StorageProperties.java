package com.example.patrol_be.common.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.util.UriUtils;

/** Every file storage path / image URL of the app (application.yml app.storage). */
@ConfigurationProperties("app.storage")
public record StorageProperties(
        /** Patrol: root; MA / other types: /{Type}. Served at /images/**. */
        String uploadRoot,
        /** MA report images (stored in DB as MA/{file}). Served at /images/MA/**. */
        String maPhotoDir,
        /** Fixed Asset audit photos (audit/, before/, after/). Served at /images/FixedAsset/**. */
        String fixedAssetPhotoDir,
        /** Forgot-password account Excel (AuthService). */
        String forgotPasswordDir,
        /** Base URL of /images/ used to embed images in exported Excel. */
        String imageBaseUrl
) {
    /** Report Type saved in maPhotoDir; also the DB prefix / URL folder (MA/{file}). */
    public static final String MA_TYPE = "MA";
    /** URL folder of fixedAssetPhotoDir under /images/. */
    private static final String FIXED_ASSET_URL_FOLDER = "FixedAsset/";

    public Path uploadRootPath() {
        return toPath(uploadRoot);
    }

    public Path maPhotoPath() {
        return toPath(maPhotoDir);
    }

    /** {upload-root}/MA: MA images saved before ma-photo-dir; null when upload-root not set. */
    public Path legacyMaPhotoPath() {
        Path root = uploadRootPath();
        return root == null ? null : root.resolve(MA_TYPE).normalize();
    }

    /**
     * MA image file (name without the MA/ prefix): ma-photo-dir when it exists
     * there, else {upload-root}/MA when it exists there, else the ma-photo-dir path.
     */
    public Path resolveMaImage(String fileName) {
        Path current = inside(maPhotoPath(), fileName);
        if (current != null && Files.exists(current)) {
            return current;
        }
        Path legacy = inside(legacyMaPhotoPath(), fileName);
        if (legacy != null && Files.exists(legacy)) {
            return legacy;
        }
        return current;
    }

    /** folder/fileName, rejecting names that leave the folder (../). */
    private static Path inside(Path folder, String fileName) {
        if (folder == null) {
            return null;
        }
        Path path = folder.resolve(fileName).normalize();
        if (!path.startsWith(folder)) {
            throw new IllegalArgumentException("Invalid image name: " + fileName);
        }
        return path;
    }

    public Path fixedAssetPhotoPath() {
        return toPath(fixedAssetPhotoDir);
    }

    /** fixedAssetPhotoDir/{folder}; null when fixedAssetPhotoDir not set. */
    public Path fixedAssetPhotoPath(FixedAssetPhotoFolder folder) {
        Path root = fixedAssetPhotoPath();
        return root == null ? null : root.resolve(folder.value()).normalize();
    }

    /**
     * Full URL of a Fixed Asset photo DB value ("before/{file}"):
     * {image-base-url}FixedAsset/{value}; null when blank or image-base-url not set.
     */
    public String fixedAssetPhotoUrl(String photo) {
        String baseUrl = imageBaseUrlWithSlash();
        if (baseUrl == null || photo == null || photo.isBlank()) {
            return null;
        }
        return baseUrl + FIXED_ASSET_URL_FOLDER + photo.trim();
    }

    /**
     * Full URL of an audit Photo3 (file name only, stored in after/):
     * {image-base-url}FixedAsset/after/{URL-encoded name}; null when blank or
     * image-base-url not set. Photo3_Old / MASTER Photo3 use fixedAssetPhotoUrl.
     */
    public String fixedAssetAfterPhotoUrl(String fileName) {
        String baseUrl = imageBaseUrlWithSlash();
        if (baseUrl == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        return baseUrl + FIXED_ASSET_URL_FOLDER + FixedAssetPhotoFolder.AFTER.value() + "/"
                + UriUtils.encodePathSegment(fileName.trim(), StandardCharsets.UTF_8);
    }

    public Path forgotPasswordPath() {
        return toPath(forgotPasswordDir);
    }

    /** imageBaseUrl ending with "/"; null when not set. */
    public String imageBaseUrlWithSlash() {
        if (imageBaseUrl == null || imageBaseUrl.isBlank()) {
            return null;
        }
        String url = imageBaseUrl.trim();
        return url.endsWith("/") ? url : url + "/";
    }

    /** Absolute, normalized path; null when blank. */
    private static Path toPath(String dir) {
        return dir == null || dir.isBlank()
                ? null
                : Paths.get(dir.trim()).toAbsolutePath().normalize();
    }
}
