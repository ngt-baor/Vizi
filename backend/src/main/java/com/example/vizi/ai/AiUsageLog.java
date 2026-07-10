package com.example.vizi.ai;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_usage_logs")
class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String feature;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected AiUsageLog() {
    }

    AiUsageLog(String feature, String model, String status, long latencyMs, String errorCode) {
        this.feature = feature;
        this.model = model;
        this.status = status;
        this.latencyMs = latencyMs;
        this.errorCode = errorCode;
    }
}
