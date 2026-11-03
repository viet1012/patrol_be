package com.example.patrol_be.common.config;


import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StorageProperties storage;

    public WebConfig(StorageProperties storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // Fixed Asset Photo3 (more specific than /images/**, so it wins).
        Path fixedAssetPhotoDir = storage.fixedAssetPhotoPath();
        if (fixedAssetPhotoDir != null) {
            registry.addResourceHandler("/images/FixedAsset/**")
                    .addResourceLocations(toLocation(fixedAssetPhotoDir))
                    .setCachePeriod(0)
                    .resourceChain(true);
        }

        // MA report images (DB value MA/{file}), more specific than /images/**:
        // ma-photo-dir first, then {upload-root}/MA (old images).
        Path maPhotoDir = storage.maPhotoPath();
        Path legacyMaPhotoDir = storage.legacyMaPhotoPath();
        if (maPhotoDir != null || legacyMaPhotoDir != null) {
            registry.addResourceHandler("/images/" + StorageProperties.MA_TYPE + "/**")
                    .addResourceLocations(Stream.of(maPhotoDir, legacyMaPhotoDir)
                            .filter(Objects::nonNull)
                            .distinct()
                            .map(WebConfig::toLocation)
                            .toArray(String[]::new))
                    .setCachePeriod(0)
                    .resourceChain(true);
        }

        Path uploadRoot = storage.uploadRootPath();
        if (uploadRoot != null) {
            registry.addResourceHandler("/images/**")
                    .addResourceLocations(toLocation(uploadRoot))
                    .setCachePeriod(0)
                    .resourceChain(true);
        }
    }

    /** file: URI of the folder, always ending with "/". */
    private static String toLocation(Path dir) {
        String location = dir.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
