package com.example.vizi.order;

import java.math.BigDecimal;

import com.example.vizi.design.Design;

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
@Table(name = "order_items")
class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private ViziOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "design_id", nullable = false)
    private Design design;

    @Column(name = "design_snapshot_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String designSnapshotJson;

    @Column(name = "print_config_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String printConfigJson;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    protected OrderItem() {
    }

    OrderItem(
            ViziOrder order,
            Design design,
            String designSnapshotJson,
            String printConfigJson,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
        this.order = order;
        this.design = design;
        this.designSnapshotJson = designSnapshotJson;
        this.printConfigJson = printConfigJson;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    Long id() {
        return id;
    }

    int quantity() {
        return quantity;
    }

    BigDecimal subtotal() {
        return subtotal;
    }
}
