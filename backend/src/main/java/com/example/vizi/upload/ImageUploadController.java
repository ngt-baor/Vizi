package com.example.vizi.upload;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping
class ImageUploadController {

    private final ImageUploadService imageUploadService;

    ImageUploadController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping("/api/uploads/images")
    @ResponseStatus(HttpStatus.CREATED)
    ImageUploadResponse uploadImage(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return imageUploadService.store(file, authentication.getName());
    }

    @GetMapping("/uploads/images/{fileName}")
    ResponseEntity<Resource> readImage(@PathVariable String fileName, Authentication authentication) {
        var image = imageUploadService.loadOwnedImage(fileName, authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.resource());
    }
}
