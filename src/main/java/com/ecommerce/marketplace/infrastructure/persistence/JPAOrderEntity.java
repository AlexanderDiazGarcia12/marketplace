package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.domain.model.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA mapping of the {@code orders} table, confined to {@code infrastructure.persistence}:
 * {@link OrderMapper} translates it to/from the pure
 * {@link com.ecommerce.marketplace.domain.model.order.Order} aggregate so no {@code @Entity} escapes
 * this package. {@code id} is a client-assigned {@code UUID} PK (minted in the domain), so the
 * adapter writes through {@code EntityManager.persist} with no {@code Persistable} dance;
 * {@code status} maps to the native {@code order_status} enum by {@link Enum#name()}. Line items are
 * an owned {@code @OneToMany} with {@code cascade = ALL} + {@code orphanRemoval}, attached only
 * through {@link #addItem(JPAOrderItemEntity)} so the bidirectional back-reference stays consistent.
 */
@Entity
@Table(name = "orders")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JPAOrderEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "order_status")
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "payment_token", nullable = false, length = 255)
    private String paymentToken;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JPAOrderItemEntity> items = new ArrayList<>();

    JPAOrderEntity(UUID id, OrderStatus status, BigDecimal totalAmount, String paymentToken, String idempotencyKey) {
        this.id = id;
        this.status = status;
        this.totalAmount = totalAmount;
        this.paymentToken = paymentToken;
        this.idempotencyKey = idempotencyKey;
    }

    void addItem(JPAOrderItemEntity item) {
        item.assignTo(this);
        this.items.add(item);
    }
}
