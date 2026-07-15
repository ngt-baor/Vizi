package com.example.vizi.template;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminTemplateRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 80) String category,
        @Size(max = 2048) String previewUrl,
        @NotNull BigDecimal widthMm,
        @NotNull BigDecimal heightMm,
        @NotBlank @Size(max = 1_000_000) String canvasJson,
        boolean active
) {
}