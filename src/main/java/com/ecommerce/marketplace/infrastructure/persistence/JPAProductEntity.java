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
 * {@code infrastructure.persistence}: it never crosses into {@code domain} or
 * the Thymeleaf
 * views — {@link ProductMapper} translates it to/from the pure {@code Product}
 * aggregate.
 *
 * <p>
 * Design notes tied to the physical schema:
 * </p>
 * <ul>
 * <li>{@code id} is the synthetic identity PK; {@code sku} is a {@code UNIQUE}
 * column, not the PK.</li>
 * <li>{@code version} carries {@code @Version} for optimistic locking; it
 * defaults to 0 in the
 * DB, matching the initial value Hibernate assigns on insert.</li>
 * <li>{@code category} reuses the domain {@link Category} enum directly (no
 * mirror enum) and maps
 * to the native {@code product_category} type via
 * {@code @JdbcTypeCode(NAMED_ENUM)}, which
 * binds by {@link Enum#name()} — exactly the constant names V1 declared.</li>
 * <li>{@code updated_at} is maintained by the
 * {@code trg_products_set_updated_at BEFORE UPDATE}
 * trigger, so it is {@code updatable = false} here: Java never writes it on
 * update. Both
 * timestamps and {@code created_at} are read back from the DB defaults after
 * insert.</li>
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
}
