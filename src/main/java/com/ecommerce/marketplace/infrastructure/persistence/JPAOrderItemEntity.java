package com.ecommerce.marketplace.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * JPA mapping of the {@code order_items} table. Owned child of {@link JPAOrderEntity}, never
 * addressed externally, hence the synthetic {@code BIGINT} identity PK; {@link OrderMapper} builds it
 * from a domain {@code OrderItem} line. {@code unit_price} is a captured price snapshot (never a live
 * join to {@code products.price}) so the price the buyer paid stays immutable even if the catalog
 * price later changes. The {@code order} back-reference is set only through
 * {@link #assignTo(JPAOrderEntity)}, keeping the bidirectional link consistent.
 */
@Entity
@Table(name = "order_items")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JPAOrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private JPAOrderEntity order;

    @Column(name = "product_sku", nullable = false, length = 64)
    private String productSku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    JPAOrderItemEntity(String productSku, int quantity, BigDecimal unitPrice) {
        this.productSku = productSku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void assignTo(JPAOrderEntity order) {
        this.order = order;
    }
}
