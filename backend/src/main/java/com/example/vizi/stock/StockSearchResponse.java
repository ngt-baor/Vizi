package com.example.vizi.stock;

import java.util.List;

record StockSearchResponse(
        int page,
        int pageSize,
        int total,
        boolean hasMore,
        String source,
        String message,
        List<StockAsset> assets
) {
}