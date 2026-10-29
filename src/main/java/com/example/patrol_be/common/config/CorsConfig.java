package com.example.patrol_be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurerz() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {

                // API
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("*")
                        .allowedHeaders("*");

                // 🔥 STATIC IMAGE (BẮT BUỘC)
                registry.addMapping("/images/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET")
                        .allowedHeaders("*");
            }
        };
    }
}
