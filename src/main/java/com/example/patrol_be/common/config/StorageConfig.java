package com.example.patrol_be.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Startup: creates missing app.storage folders (warning only, never crashes)
 * and logs every key with its actual absolute path.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
    private final StorageProperties storage;

    public StorageConfig(StorageProperties storage) {
        this.storage = storage;
    }

    @PostConstruct
    void initStorage() {
        Map<String, Path> dirs = new LinkedHashMap<>();
        dirs.put("upload-root", storage.uploadRootPath());
        dirs.put("ma-photo-dir", storage.maPhotoPath());
        dirs.put("fixed-asset-photo-dir", storage.fixedAssetPhotoPath());
        // Every sub folder is always listed: "(not set)" when the root is not set.
        for (FixedAssetPhotoFolder folder : FixedAssetPhotoFolder.values()) {
            dirs.put("fixed-asset-photo-dir/" + folder.value(), storage.fixedAssetPhotoPath(folder));
        }
        dirs.put("forgot-password-dir", storage.forgotPasswordPath());

        StringBuilder block = new StringBuilder("app.storage:");
        dirs.forEach((key, dir) -> block.append("\n  ").append(key).append(" = ")
                .append(dir == null ? "(not set)" : dir + ensureDir(key, dir)));
        String imageBaseUrl = storage.imageBaseUrlWithSlash();
        block.append("\n  image-base-url = ").append(imageBaseUrl == null ? "(not set)" : imageBaseUrl);
        log.info(block.toString());
    }

    /** Creates the folder when missing; returns a status suffix for the log block. */
    private static String ensureDir(String key, Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException | RuntimeException exception) {
            log.warn("app.storage.{}: cannot create {}", key, dir, exception);
            return " [NOT CREATED]";
        }
        if (!Files.isWritable(dir)) {
            log.warn("app.storage.{}: {} is not writable", key, dir);
            return " [NOT WRITABLE]";
        }
        return "";
    }
}
