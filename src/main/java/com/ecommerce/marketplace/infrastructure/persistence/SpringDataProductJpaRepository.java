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
     * Looks up a live product by SKU, excluding soft-deleted rows ({@code deleted_at IS NOT NULL}).
     * A derived query name cannot express the {@code deletedAt IS NULL} predicate, so it is written
     * out as JPQL. Soft delete itself is delivered by US-12; this query is already correct for it,
     * so no retrofit is needed once deletion starts stamping {@code deleted_at}.
     */
    @Query("SELECT p FROM JPAProductEntity p WHERE p.sku = :sku AND p.deletedAt IS NULL")
    Optional<JPAProductEntity> findBySku(@Param("sku") String sku);

    /**
     * Paginated catalog search (US-13), written as native SQL for two reasons the trgm GIN indexes
     * from US-09 depend on:
     * <ul>
     *   <li>{@code ILIKE '%term%'} on {@code name}/{@code description} keeps the column unwrapped
     *       (no {@code LOWER(col)} that would defeat {@code idx_products_name_trgm} /
     *       {@code idx_products_description_trgm}). pg_trgm's GIN index answers {@code ILIKE}
     *       case-insensitively without any function on the column, so the predicate stays sargable.</li>
     *   <li>The {@code category = CAST(:category AS product_category)} cast matches the native enum
     *       type and lets the planner use the partial {@code idx_products_category
     *       WHERE deleted_at IS NULL}.</li>
     * </ul>
     * Absent filters are expressed with {@code :param IS NULL OR ...} so a single query serves the
     * text-only, category-only, both, and neither cases. {@code deleted_at IS NULL} drops
     * soft-deleted rows. Ordering is {@code name, id} for a deterministic, stable page window.
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
     * Idempotent upsert by SKU (US-17), the physical implementation of {@code
     * ProductRepositoryPort.upsertBySku}. A single atomic {@code INSERT ... ON CONFLICT (sku) DO
     * UPDATE} over {@code uq_products_sku} (US-09): a brand-new SKU inserts with {@code version = 0};
     * a re-delivered/changed SKU updates every business field and bumps {@code version =
     * products.version + 1}. The bump is done in SQL, entirely outside Hibernate's {@code @Version}
     * lifecycle (this write never loads a managed entity), so checkout's optimistic locking stays
     * correct — never {@code EXCLUDED.version}, never a reset. The V3 {@code BEFORE UPDATE} trigger
     * still fires on the conflict path and refreshes {@code updated_at}.
     *
     * <p>The {@code WHERE products.deleted_at IS NULL} guard makes the upsert refuse to resurrect a
     * soft-deleted product (US-12): a soft delete is a deliberate business decision an automated
     * import must not silently revert. When the conflicting row is soft-deleted the DO UPDATE is
     * skipped and — because the conflict was already resolved — no INSERT happens either, so {@code
     * RETURNING} yields no row. The adapter reads that empty result as "no live row to upsert" and
     * reports {@link com.ecommerce.marketplace.domain.failure.Failure.ProductNotFound}.
     *
     * <p>Native (not JPQL) because {@code ON CONFLICT}, the {@code product_category} enum cast and
     * {@code RETURNING *} have no JPQL equivalent. {@code @Modifying(flushAutomatically = true,
     * clearAutomatically = true)} keeps the persistence context consistent with the row this
     * statement wrote directly in the database.
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
     * Versioned stock decrement for checkout (US-22), the physical implementation of
     * {@code ProductRepositoryPort.decreaseStock}. An atomic {@code UPDATE ... SET stock = stock - ?,
     * version = version + 1 WHERE sku = ? AND deleted_at IS NULL AND version = ?}: the caller has
     * already validated (via the domain rule) that the row it read has enough stock, so a zero-row
     * result means only that a concurrent writer advanced {@code version}. Returning zero rows is a
     * normal statement outcome — not an {@code OptimisticLockException} — so it never marks the
     * checkout transaction rollback-only, which is what makes the caller's in-transaction bounded
     * retry possible (a managed {@code merge}/{@code flush} conflict would poison the whole
     * transaction instead). The version bump is done in SQL, outside Hibernate's {@code @Version}
     * lifecycle, exactly like {@link #upsertBySku}. {@code RETURNING *} hands the updated row back so
     * the adapter maps the new state without a second read; {@code @Modifying(clearAutomatically =
     * true)} keeps the persistence context consistent with the row this statement wrote directly,
     * so the next retry's re-read sees fresh committed state.
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
