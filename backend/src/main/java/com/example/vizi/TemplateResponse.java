package com.example.vizi;

import java.math.BigDecimal;

record TemplateListItem(
        Long id,
        String name,
        String category,
        String previewUrl,
        BigDecimal widthMm,
        BigDecimal heightMm
) {
    static TemplateListItem from(Template template) {
        return new TemplateListItem(
                template.id(),
                template.name(),
                template.category(),
                template.previewUrl(),
                template.widthMm(),
                template.heightMm()
        );
    }
}

record TemplateDetail(
        Long id,
        String name,
        String category,
        String previewUrl,
        BigDecimal widthMm,
        BigDecimal heightMm,
        String canvasJson
) {
    static TemplateDetail from(Template template) {
        return new TemplateDetail(
                template.id(),
                template.name(),
                template.category(),
                template.previewUrl(),
                template.widthMm(),
                template.heightMm(),
                template.canvasJson()
        );
    }
}
