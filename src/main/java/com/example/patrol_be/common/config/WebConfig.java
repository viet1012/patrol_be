package com.example.patrol_be.common.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:/D:/Patrol App/API/uploaded_images/")
//                .addResourceLocations("file:/app/uploaded_images/")

                .setCachePeriod(0)
                .resourceChain(true);
    }
}
