package com.example.patrol_be.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:/D:/1.pc/Patrol App/uploaded_images/")
//                .addResourceLocations("file:/app/uploaded_images/")

                .setCachePeriod(0)
                .resourceChain(true);
    }
}
