package com.example.vizi.paper;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "paper_stocks")
class PaperStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 500)
    private String description;

    private Integer gsm;

    @Column(name = "price_per_100", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePer100;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaperStock() {
    }

    PaperStock(
            String code,
            String name,
            String description,
            Integer gsm,
            BigDecimal pricePer100,
            String status,
            boolean active
    ) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.gsm = gsm;
        this.pricePer100 = pricePer100;
        this.status = status;
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void update(
            String code,
            String name,
            String description,
            Integer gsm,
            BigDecimal pricePer100,
            String status,
            boolean active
    ) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.gsm = gsm;
        this.pricePer100 = pricePer100;
        this.status = status;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    Long id() {
        return id;
    }

    String code() {
        return code;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    Integer gsm() {
        return gsm;
    }

    BigDecimal pricePer100() {
        return pricePer100;
    }

    String status() {
        return status;
    }

    boolean active() {
        return active;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}