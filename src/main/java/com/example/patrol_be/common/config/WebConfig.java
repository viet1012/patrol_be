package com.example.patrol_be.common.config;


import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${fixed-asset.photo-dir:}")
    private String fixedAssetPhotoDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // Fixed Asset Photo3 (more specific than /images/**, so it wins).
        if (!fixedAssetPhotoDir.isBlank()) {
            String location = Paths.get(fixedAssetPhotoDir.trim()).toAbsolutePath().normalize()
                    .toUri().toString();
            registry.addResourceHandler("/images/FixedAsset/**")
                    .addResourceLocations(location.endsWith("/") ? location : location + "/")
                    .setCachePeriod(0)
                    .resourceChain(true);
        }

        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:/D:/Patrol App/API/uploaded_images/")
                .setCachePeriod(0)
                .resourceChain(true);
    }
}
