package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SpringDataProductJpaRepository extends JpaRepository<JPAProductEntity, Long> {

    /**
     * Looks up a live product by SKU, excluding soft-deleted rows. A derived query name cannot
     * express the {@code deletedAt IS NULL} predicate, so it is written as JPQL.
     */
    @Query("SELECT p FROM JPAProductEntity p WHERE p.sku = :sku AND p.deletedAt IS NULL")
    Optional<JPAProductEntity> findBySku(@Param("sku") String sku);

    /**
     * Paginated catalog search, written as native SQL for two reasons the trgm GIN indexes depend on:
     * <ul>
     *   <li>{@code ILIKE '%term%'} on {@code name}/{@code description} keeps the column unwrapped (no
     *       {@code LOWER(col)}), so the pg_trgm GIN index answers it case-insensitively and the
     *       predicate stays sargable.</li>
     *   <li>The {@code category = CAST(:category AS product_category)} cast matches the native enum
     *       type and lets the planner use the partial category index.</li>
     * </ul>
     * Absent filters are expressed with {@code :param IS NULL OR ...} so one query serves every
     * combination; soft-deleted rows are dropped and ordering is {@code name, id} for a stable page.
     */
    @Query(value = """
            SELECT * FROM products p
            WHERE p.deleted_at IS NULL
              AND (:searchText IS NULL
                   OR p.name ILIKE '%' || :searchText || '%'
                   OR p.description ILIKE '%' || :searchText || '%')
              AND (CAST(:category AS text) IS NULL
                   OR p.category = CAST(:category AS product_category))
            ORDER BY p.name ASC, p.id ASC
            """,
            nativeQuery = true)
    List<JPAProductEntity> search(
            @Param("searchText") String searchText,
            @Param("category") String category,
            Pageable pageable);

    @Query(value = """
            SELECT count(*) FROM products p
            WHERE p.deleted_at IS NULL
              AND (:searchText IS NULL
                   OR p.name ILIKE '%' || :searchText || '%'
                   OR p.description ILIKE '%' || :searchText || '%')
              AND (CAST(:category AS text) IS NULL
                   OR p.category = CAST(:category AS product_category))
            """,
            nativeQuery = true)
    long countSearch(
            @Param("searchText") String searchText,
            @Param("category") String category);

    /**
     * Idempotent upsert by SKU: a single atomic {@code INSERT ... ON CONFLICT (sku) DO UPDATE}. A new
     * SKU inserts with {@code version = 0}; a re-delivered SKU updates every business field and bumps
     * {@code version = products.version + 1} in SQL, entirely outside Hibernate's {@code @Version}
     * lifecycle, so checkout's optimistic locking stays correct. The {@code WHERE deleted_at IS NULL}
     * guard refuses to resurrect a soft-deleted product: the DO UPDATE is skipped and {@code RETURNING}
     * yields no row, which the adapter reads as {@link com.ecommerce.marketplace.domain.failure.Failure.ProductNotFound}.
     * Native because {@code ON CONFLICT}, the enum cast and {@code RETURNING *} have no JPQL equivalent.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO products (sku, name, description, category, price, stock, weight_kg, version)
            VALUES (:sku, :name, :description, CAST(:category AS product_category), :price, :stock, :weightKg, 0)
            ON CONFLICT (sku) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                category = EXCLUDED.category,
                price = EXCLUDED.price,
                stock = EXCLUDED.stock,
                weight_kg = EXCLUDED.weight_kg,
                version = products.version + 1
            WHERE products.deleted_at IS NULL
            RETURNING *
            """,
            nativeQuery = true)
    List<JPAProductEntity> upsertBySku(
            @Param("sku") String sku,
            @Param("name") String name,
            @Param("description") String description,
            @Param("category") String category,
            @Param("price") BigDecimal price,
            @Param("stock") int stock,
            @Param("weightKg") BigDecimal weightKg);

    /**
     * Versioned stock decrement for checkout: an atomic
     * {@code UPDATE ... WHERE sku = ? AND deleted_at IS NULL AND version = ?}. The caller has already
     * validated the row it read has enough stock, so a zero-row result means only that a concurrent
     * writer advanced {@code version}. Returning zero rows is a normal outcome — not an
     * {@code OptimisticLockException} — so it never marks the checkout transaction rollback-only, which
     * is what makes the caller's in-transaction bounded retry possible. The version bump is done in SQL,
     * outside Hibernate's {@code @Version} lifecycle; {@code RETURNING *} hands the updated row back.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products
            SET stock = stock - :quantity, version = version + 1
            WHERE sku = :sku AND deleted_at IS NULL AND version = :expectedVersion
            RETURNING *
            """,
            nativeQuery = true)
    List<JPAProductEntity> decreaseStockIfVersionMatches(
            @Param("sku") String sku,
            @Param("quantity") int quantity,
            @Param("expectedVersion") int expectedVersion);
}
