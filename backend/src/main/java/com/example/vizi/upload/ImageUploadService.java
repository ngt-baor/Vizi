package com.example.vizi.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.example.vizi.auth.AuthService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class ImageUploadService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp"
    );

    private final Path imageDirectory;
    private final AssetRepository assetRepository;
    private final AuthService authService;

    ImageUploadService(
            @Value("${app.upload.image-dir:.vizi-uploads/images}") String imageDirectory,
            AssetRepository assetRepository,
            AuthService authService
    ) {
        this.imageDirectory = Path.of(imageDirectory).toAbsolutePath().normalize();
        this.assetRepository = assetRepository;
        this.authService = authService;
    }

    @Transactional
    ImageUploadResponse store(MultipartFile file, String email) {
        validateFile(file);

        var user = authService.requireUser(email);
        var contentType = normalizeContentType(file.getContentType());
        var extension = EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        var fileName = UUID.randomUUID() + "." + extension;
        var storageKey = "images/" + fileName;
        var fileUrl = "/uploads/" + storageKey;
        var target = imageDirectory.resolve(fileName).normalize();
        if (!target.startsWith(imageDirectory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
        }

        try {
            Files.createDirectories(imageDirectory);
            file.transferTo(target);
            var asset = assetRepository.saveAndFlush(new Asset(
                    user,
                    fileUrl,
                    fileName,
                    contentType,
                    file.getSize()
            ));
            return new ImageUploadResponse(
                    asset.id(),
                    fileName,
                    contentType,
                    file.getSize(),
                    storageKey,
                    fileUrl
            );
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot store image");
        } catch (RuntimeException exception) {
            deleteQuietly(target);
            throw exception;
        }
    }

    private static void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Preserve the original storage/database failure.
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 5 MB or smaller");
        }

        var contentType = normalizeContentType(file.getContentType());
        var expectedExtension = EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        if (expectedExtension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PNG, JPG, or WebP images are allowed");
        }
        if (!hasAllowedExtension(file.getOriginalFilename(), expectedExtension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image extension does not match the file type");
        }
        if (!hasExpectedMagicBytes(file, contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image content does not match the file type");
        }
    }

    private static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    }

    private static boolean hasAllowedExtension(String originalName, String expectedExtension) {
        if (originalName == null) {
            return false;
        }
        var lowerName = originalName.toLowerCase(Locale.ROOT);
        return switch (expectedExtension) {
            case "jpg" -> lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
            default -> lowerName.endsWith("." + expectedExtension);
        };
    }

    private static boolean hasExpectedMagicBytes(MultipartFile file, String contentType) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            return switch (contentType) {
                case "image/png" -> startsWith(header, new byte[] {
                        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                });
                case "image/jpeg" -> startsWith(header, new byte[] {
                        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
                });
                case "image/webp" -> startsWith(header, new byte[] {
                        0x52, 0x49, 0x46, 0x46
                }) && header.length >= 12
                        && header[8] == 0x57
                        && header[9] == 0x45
                        && header[10] == 0x42
                        && header[11] == 0x50;
                default -> false;
            };
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
