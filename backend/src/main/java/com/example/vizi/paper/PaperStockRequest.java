package com.example.vizi.paper;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaperStockRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 500) String description,
        @Min(1) @Max(2000) Integer gsm,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal pricePer100,
        @NotBlank @Pattern(regexp = "IN_STOCK|OUT_OF_STOCK") String status,
        boolean active
) {
}