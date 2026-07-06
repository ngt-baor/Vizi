package com.example.vizi.design;

import java.time.Instant;

import com.example.vizi.auth.User;

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
@Table(name = "design_snapshots")
class DesignSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_id", nullable = false)
    private Design design;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(name = "canvas_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String canvasJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DesignSnapshot() {
    }

    DesignSnapshot(Design design, User user, String reason, String canvasJson) {
        this.design = design;
        this.user = user;
        this.reason = reason;
        this.canvasJson = canvasJson;
    }

    String reason() {
        return reason;
    }

    String canvasJson() {
        return canvasJson;
    }
}
