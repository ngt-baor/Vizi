package com.example.vizi.upload;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
class ImageUploadController {

    private final ImageUploadService imageUploadService;

    ImageUploadController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    ImageUploadResponse uploadImage(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return imageUploadService.store(file, authentication.getName());
    }
}
