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
 * JPA mapping of the {@code products} table (V2/V3). Lives strictly inside
 * {@code infrastructure.persistence}: it never crosses into {@code domain} or the Thymeleaf
 * views — {@link ProductMapper} translates it to/from the pure {@code Product} aggregate.
 *
 * <p>Design notes tied to the physical schema:</p>
 * <ul>
 *   <li>{@code id} is the synthetic identity PK; {@code sku} is a {@code UNIQUE} column, not the PK.</li>
 *   <li>{@code version} carries {@code @Version} for optimistic locking; it defaults to 0 in the
 *       DB, matching the initial value Hibernate assigns on insert.</li>
 *   <li>{@code category} reuses the domain {@link Category} enum directly (no mirror enum) and maps
 *       to the native {@code product_category} type via {@code @JdbcTypeCode(NAMED_ENUM)}, which
 *       binds by {@link Enum#name()} — exactly the constant names V1 declared.</li>
 *   <li>{@code updated_at} is maintained by the {@code trg_products_set_updated_at BEFORE UPDATE}
 *       trigger, so it is {@code updatable = false} here: Java never writes it on update. Both
 *       timestamps and {@code created_at} are read back from the DB defaults after insert.</li>
 * </ul>
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
     * loaded (US-11). Merging it makes Hibernate compare {@code expectedVersion} against the row's
     * actual stored version: a concurrent edit that already advanced it raises an optimistic-lock
     * failure the adapter translates to
     * {@link com.ecommerce.marketplace.domain.failure.Failure.ConcurrentStockConflict} — never a
     * silent lost update. The identity fields (and {@code deletedAt}, so a concurrent soft delete
     * is never resurrected by an in-flight edit) are copied here rather than exposed through generic
     * setters, keeping the entity's mutation surface closed.
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
     * Stamps {@code deleted_at} in place on this <em>managed</em> row (US-12). Because the instance
     * stays attached to the persistence context, Hibernate's dirty checking issues a versioned
     * {@code UPDATE ... WHERE id = ? AND version = ?} on flush, advancing {@code @Version}
     * automatically — no {@code detach}/{@code merge} dance is needed here (that is only required by
     * {@link #editedTo} to reconcile the editor's expected version). The version bump is what closes
     * the race with an in-flight edit: a concurrent edit carrying the pre-delete version can no
     * longer merge, so it fails with an optimistic-lock conflict instead of resurrecting the row.
     * Mutating {@code deletedAt} through this narrow method rather than a generic setter keeps the
     * entity's mutation surface closed, matching the {@link #editedTo} convention.
     */
    void markDeleted(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
