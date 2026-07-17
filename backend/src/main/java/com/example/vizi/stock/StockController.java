package com.example.vizi.stock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StockController {

    private final StockService stockService;

    StockController(StockService stockService) {
        this.stockService = stockService;
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