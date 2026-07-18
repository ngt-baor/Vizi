package com.example.vizi.paper;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperStockResponse(
        Long id,
        String code,
        String name,
        String description,
        Integer gsm,
        BigDecimal pricePer100,
        String status,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    static PaperStockResponse from(PaperStock paper) {
        return new PaperStockResponse(
                paper.id(),
                paper.code(),
                paper.name(),
                paper.description(),
                paper.gsm(),
                paper.pricePer100(),
                paper.status(),
                paper.active(),
                paper.createdAt(),
                paper.updatedAt()
        );
    }
}