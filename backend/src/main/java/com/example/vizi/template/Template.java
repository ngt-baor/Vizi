package com.example.vizi.template;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "templates")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(name = "preview_url", length = 2048)
    private String previewUrl;

    @Column(name = "width_mm", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthMm;

    @Column(name = "height_mm", nullable = false, precision = 8, scale = 2)
    private BigDecimal heightMm;

    @Column(name = "canvas_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String canvasJson;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Template() {
    }

    public Template(
            String name,
            String category,
            String previewUrl,
            BigDecimal widthMm,
            BigDecimal heightMm,
            String canvasJson,
            boolean active
    ) {
        this.name = name;
        this.category = category;
        this.previewUrl = previewUrl;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.canvasJson = canvasJson;
        this.active = active;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    String category() {
        return category;
    }

    String previewUrl() {
        return previewUrl;
    }

    public BigDecimal widthMm() {
        return widthMm;
    }

    public BigDecimal heightMm() {
        return heightMm;
    }

    public String canvasJson() {
        return canvasJson;
    }

    boolean active() {
        return active;
    }

    Instant createdAt() {
        return createdAt;
    }
}
