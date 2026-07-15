package com.example.vizi.design;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDesignRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull @DecimalMin("10.0") @DecimalMax("200.0") BigDecimal widthMm,
        @NotNull @DecimalMin("10.0") @DecimalMax("200.0") BigDecimal heightMm,
        @Size(max = 500_000) String canvasJson
) {
}
