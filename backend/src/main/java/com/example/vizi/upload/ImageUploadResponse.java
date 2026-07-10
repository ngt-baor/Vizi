package com.example.vizi.upload;

record ImageUploadResponse(
        Long assetId,
        String fileName,
        String contentType,
        long sizeBytes,
        String storageKey,
        String url
) {
}
