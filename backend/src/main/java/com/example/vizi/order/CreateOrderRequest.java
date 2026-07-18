package com.example.vizi.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull Long designId,
        @NotBlank @Size(max = 64) String paper,
        @Min(1) @Max(100_000) int quantity,
        boolean roundedCorners,
        @Size(max = 1000) String customerNote
) {
}
