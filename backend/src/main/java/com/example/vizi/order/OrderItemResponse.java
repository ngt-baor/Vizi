package com.example.vizi.order;

import java.math.BigDecimal;

import com.example.vizi.design.Design;

public record OrderItemResponse(
        Long id,
        int quantity,
        BigDecimal subtotal,
        Long designId,
        String designName,
        String designSnapshotJson,
        BigDecimal widthMm,
        BigDecimal heightMm,
        String printConfigJson
) {
    static OrderItemResponse from(OrderItem item) {
        Design design = item.design();
        return new OrderItemResponse(
                item.id(),
                item.quantity(),
                item.subtotal(),
                design.id(),
                design.name(),
                item.designSnapshotJson(),
                design.widthMm(),
                design.heightMm(),
                item.printConfigJson()
        );
    }
}
