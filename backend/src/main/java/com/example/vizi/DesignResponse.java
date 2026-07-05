package com.example.vizi;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record DesignDetail(
        Long id,
        Long templateId,
        String name,
        String canvasJson,
        BigDecimal widthMm,
        BigDecimal heightMm,
        Instant updatedAt
) {

    static DesignDetail from(Design design) {
        return new DesignDetail(
                design.id(),
                design.templateId(),
                design.name(),
                design.canvasJson(),
                design.widthMm(),
                design.heightMm(),
                design.updatedAt()
        );
    }
}

record SaveDesignRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 1_000_000) String canvasJson
) {
}
