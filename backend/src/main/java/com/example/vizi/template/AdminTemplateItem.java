package com.example.vizi.template;

import java.math.BigDecimal;

public record AdminTemplateItem(
        Long id,
        String name,
        String category,
        String previewUrl,
        BigDecimal widthMm,
        BigDecimal heightMm,
        String canvasJson,
        boolean active
) {
    static AdminTemplateItem from(Template template) {
        return new AdminTemplateItem(
                template.id(),
                template.name(),
                template.category(),
                template.previewUrl(),
                template.widthMm(),
                template.heightMm(),
                template.canvasJson(),
                template.active()
        );
    }
}