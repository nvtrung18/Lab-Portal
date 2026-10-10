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

    @Value("${app.storage.website-image-path:uploads/website}")
    private String websiteImagePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadLocation = Paths.get(cvStoragePath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/cv/**")
                .addResourceLocations(uploadLocation);
        String websiteLocation = Paths.get(websiteImagePath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/website/**")
                .addResourceLocations(websiteLocation.endsWith("/") ? websiteLocation : websiteLocation + "/");
    }
}
