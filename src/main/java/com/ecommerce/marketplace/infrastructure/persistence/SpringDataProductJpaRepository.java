package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
