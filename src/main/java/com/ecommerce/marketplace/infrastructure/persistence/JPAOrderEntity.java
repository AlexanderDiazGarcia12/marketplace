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
 * JPA mapping of the {@code orders} table (V8). Lives strictly inside
 * {@code infrastructure.persistence}: it never crosses into {@code domain}/{@code application} or
 * the views — {@link OrderMapper} translates it to/from the pure {@link com.ecommerce.marketplace.domain.model.order.Order}
 * aggregate so no {@code @Entity} escapes this package.
 *
 * <p>Design notes tied to the physical schema:</p>
 * <ul>
 *   <li>{@code id} is a client-assigned {@code UUID} PK ({@code OrderId.generate()} always mints it
 *       in the domain before persist), so it carries no {@code @GeneratedValue} — matching
 *       {@code ImportJobEntity}. The adapter writes through {@code EntityManager.persist}, which
 *       always INSERTs, so no {@code Persistable} dance is needed (unlike {@code IdempotencyKeyEntity},
 *       which routes through {@code JpaRepository.save}).</li>
 *   <li>{@code status} reuses the domain {@link OrderStatus} enum directly (no mirror enum) and maps
 *       to the native {@code order_status} type via {@code @JdbcTypeCode(NAMED_ENUM)} (binds by
 *       {@link Enum#name()} — {@code CONFIRMED}/{@code REJECTED}), matching the
 *       {@code products.category} convention.</li>
 *   <li>{@code created_at} is DB-defaulted ({@code now()}), so it is {@code insertable = false} and
 *       read back after insert.</li>
 *   <li>Line items are an owned {@code @OneToMany} with {@code cascade = ALL} + {@code orphanRemoval}:
 *       persisting the order cascades the inserts, matching the {@code order_items} rows being
 *       meaningless without their parent (the FK is {@code ON DELETE CASCADE}).</li>
 * </ul>
 *
 * <p>Accessors are package-private (Lombok {@code @Getter(PACKAGE)}) with no generic setters,
 * matching {@code JPAProductEntity}/{@code OutboxEventEntity} — the entity cannot leak out of this
 * package. Items are attached only through {@link #addItem(JPAOrderItemEntity)}, which keeps the
 * bidirectional back-reference consistent, keeping the mutation surface closed.</p>
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
