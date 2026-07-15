package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.domain.model.product.Category;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JPA mapping of the {@code products} table, confined to {@code infrastructure.persistence}:
 * {@link ProductMapper} translates it to/from the pure {@code Product} aggregate. {@code id} is the
 * synthetic identity PK and {@code sku} a {@code UNIQUE} column; {@code version} carries
 * {@code @Version} for optimistic locking; {@code category} maps to the native
 * {@code product_category} enum by {@link Enum#name()}. {@code created_at}/{@code updated_at} are
 * DB-maintained (a trigger refreshes {@code updated_at}), so they are {@code updatable = false} and
 * read back after writes.
 */
@Entity
@Table(name = "products")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JPAProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "sku", nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Basic(optional = false)
    @Column(name = "category", nullable = false, columnDefinition = "product_category")
    private Category category;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "weight_kg", nullable = false, precision = 9, scale = 3)
    private BigDecimal weightKg;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    JPAProductEntity(
            String sku,
            String name,
            String description,
            Category category,
            BigDecimal price,
            int stock,
            BigDecimal weightKg,
            int version) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.weightKg = weightKg;
        this.version = version;
    }

    /**
     * Builds a <em>detached</em> row that reuses this managed row's identity ({@code id}, {@code sku})
     * but carries the caller-supplied editable fields and the {@code expectedVersion} the editor
     * loaded. Merging it makes Hibernate compare {@code expectedVersion} against the stored version,
     * so a concurrent edit that already advanced it raises an optimistic-lock failure the adapter
     * translates to {@link com.ecommerce.marketplace.domain.failure.Failure.ConcurrentStockConflict}
     * rather than a silent lost update. Identity fields (and {@code deletedAt}, so a concurrent soft
     * delete is never resurrected) are copied here rather than exposed through generic setters.
     */
    JPAProductEntity editedTo(
            String name,
            String description,
            Category category,
            BigDecimal price,
            int stock,
            BigDecimal weightKg,
            int expectedVersion) {
        JPAProductEntity edited = new JPAProductEntity(this.sku, name, description, category, price, stock, weightKg, expectedVersion);
        edited.id = this.id;
        edited.deletedAt = this.deletedAt;
        return edited;
    }

    /**
     * Stamps {@code deleted_at} in place on this <em>managed</em> row. Staying attached lets
     * Hibernate's dirty checking flush a versioned {@code UPDATE ... WHERE id = ? AND version = ?}
     * that advances {@code @Version} automatically — closing the race with an in-flight edit, whose
     * stale-version merge can then no longer resurrect the row. Mutating {@code deletedAt} through
     * this narrow method rather than a generic setter keeps the entity's mutation surface closed.
     */
    void markDeleted(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
