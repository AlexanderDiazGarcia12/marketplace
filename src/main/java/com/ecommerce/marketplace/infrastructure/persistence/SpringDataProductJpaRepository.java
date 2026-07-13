package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
