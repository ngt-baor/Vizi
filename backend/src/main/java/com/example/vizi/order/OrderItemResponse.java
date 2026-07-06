package com.example.vizi.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        int quantity,
        BigDecimal subtotal
) {
    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.id(), item.quantity(), item.subtotal());
    }
}
