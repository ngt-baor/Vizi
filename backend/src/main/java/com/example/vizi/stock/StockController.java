package com.example.vizi.stock;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StockController {

    private final StockService stockService;

    StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/api/stock/images/{id}")
    ResponseEntity<byte[]> image(@PathVariable String id) {
        var image = stockService.image(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.content());
    }

    @GetMapping("/api/stock/search")
    StockSearchResponse search(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "12") int pageSize
    ) {
        return stockService.search(query, kind, page, pageSize);
    }
}