package com.example.vizi.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items
) {
    static OrderResponse from(ViziOrder order) {
        return new OrderResponse(
                order.id(),
                order.status(),
                order.totalAmount(),
                order.items().stream().map(OrderItemResponse::from).toList()
        );
    }
}
