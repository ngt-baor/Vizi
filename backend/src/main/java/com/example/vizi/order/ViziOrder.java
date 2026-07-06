package com.example.vizi.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.vizi.auth.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
class ViziOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "customer_note")
    private String customerNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected ViziOrder() {
    }

    ViziOrder(User user, String status, BigDecimal totalAmount, String customerNote) {
        this.user = user;
        this.status = status;
        this.totalAmount = totalAmount;
        this.customerNote = customerNote;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void addItem(OrderItem item) {
        items.add(item);
    }

    Long id() {
        return id;
    }

    String status() {
        return status;
    }

    BigDecimal totalAmount() {
        return totalAmount;
    }

    List<OrderItem> items() {
        return items;
    }
}
