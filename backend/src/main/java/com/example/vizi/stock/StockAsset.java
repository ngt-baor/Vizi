package com.example.vizi.stock;

import java.util.List;

record StockAsset(
        String id,
        String title,
        String kind,
        String collection,
        String previewUrl,
        String sourceUrl,
        String creator,
        String license,
        String licenseVersion,
        List<String> tags,
        String credit
) {
}