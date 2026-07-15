package com.example.vizi.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Long userId,
        String userEmail,
        String userFullName,
        String customerNote,
        Instant createdAt,
        Instant updatedAt
) {
    static OrderResponse from(ViziOrder order) {
        var user = order.user();
        return new OrderResponse(
                order.id(),
                order.status(),
                order.totalAmount(),
                order.items().stream().map(OrderItemResponse::from).toList(),
                user == null ? null : user.id(),
                user == null ? null : user.email(),
                user == null ? null : user.fullName(),
                order.customerNote(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}
