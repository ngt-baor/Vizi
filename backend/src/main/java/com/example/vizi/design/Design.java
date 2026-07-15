package com.example.vizi.design;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.vizi.auth.User;
import com.example.vizi.template.Template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "designs")
public class Design {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private Template template;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "canvas_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String canvasJson;

    @Column(name = "width_mm", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthMm;

    @Column(name = "height_mm", nullable = false, precision = 8, scale = 2)
    private BigDecimal heightMm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Design() {
    }

    Design(User user, Template template) {
        this.user = user;
        this.template = template;
        this.name = template.name();
        this.canvasJson = template.canvasJson();
        this.widthMm = template.widthMm();
        this.heightMm = template.heightMm();
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    Design(User user, String name, String canvasJson, BigDecimal widthMm, BigDecimal heightMm) {
        this.user = user;
        this.template = null;
        this.name = name;
        this.canvasJson = canvasJson;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return user == null ? null : user.id();
    }

    public String userEmail() {
        return user == null ? null : user.email();
    }

    Long templateId() {
        return template == null ? null : template.id();
    }

    public String name() {
        return name;
    }

    public String canvasJson() {
        return canvasJson;
    }

    public BigDecimal widthMm() {
        return widthMm;
    }

    public BigDecimal heightMm() {
        return heightMm;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    void update(String name, String canvasJson) {
        this.name = name;
        this.canvasJson = canvasJson;
        this.updatedAt = Instant.now();
    }
}
