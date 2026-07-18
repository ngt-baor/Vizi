package com.example.vizi.paper;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/papers")
class AdminPaperStockController {

    private final PaperCatalogService paperCatalogService;

    AdminPaperStockController(PaperCatalogService paperCatalogService) {
        this.paperCatalogService = paperCatalogService;
    }

    @GetMapping
    List<PaperStockResponse> listPapers(Authentication authentication) {
        return paperCatalogService.listForAdmin(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PaperStockResponse createPaper(
            @Valid @RequestBody PaperStockRequest request,
            Authentication authentication
    ) {
        return paperCatalogService.createForAdmin(authentication.getName(), request);
    }

    @PutMapping("/{id}")
    PaperStockResponse updatePaper(
            @PathVariable Long id,
            @Valid @RequestBody PaperStockRequest request,
            Authentication authentication
    ) {
        return paperCatalogService.updateForAdmin(id, authentication.getName(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePaper(@PathVariable Long id, Authentication authentication) {
        paperCatalogService.deleteForAdmin(id, authentication.getName());
    }
}