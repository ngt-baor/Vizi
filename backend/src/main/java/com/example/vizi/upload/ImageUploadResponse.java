package com.example.vizi.upload;

record ImageUploadResponse(
        String fileName,
        String contentType,
        long sizeBytes,
        String storageKey,
        String url
) {
}
