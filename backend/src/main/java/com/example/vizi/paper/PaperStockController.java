package com.example.vizi.paper;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/papers")
class PaperStockController {

    private final PaperCatalogService paperCatalogService;

    PaperStockController(PaperCatalogService paperCatalogService) {
        this.paperCatalogService = paperCatalogService;
    }

    @GetMapping
    List<PaperStockResponse> listPapers() {
        return paperCatalogService.listPublic();
    }
}