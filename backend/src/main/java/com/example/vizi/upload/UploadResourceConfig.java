package com.example.vizi.upload;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class UploadResourceConfig implements WebMvcConfigurer {

    private final Path imageDirectory;

    UploadResourceConfig(@Value("${app.upload.image-dir:.vizi-uploads/images}") String imageDirectory) {
        this.imageDirectory = Path.of(imageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations(imageDirectory.toUri().toString());
    }
}
