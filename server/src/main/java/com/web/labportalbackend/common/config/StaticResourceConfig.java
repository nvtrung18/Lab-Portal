package com.web.labportalbackend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.storage.cv-path:uploads/cv}")
    private String cvStoragePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadLocation = Paths.get(cvStoragePath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/cv/**")
                .addResourceLocations(uploadLocation);
    }
}
