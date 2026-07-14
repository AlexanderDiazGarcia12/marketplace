package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository over {@code orders} (US-22). The order is always read back with its
 * line items in a single fetch join, so the mapper can rebuild the aggregate without relying on
 * lazy loading outside a transaction ({@code open-in-view} is disabled).
 */
public interface SpringDataOrderJpaRepository extends JpaRepository<JPAOrderEntity, UUID> {

    @Query("SELECT o FROM JPAOrderEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<JPAOrderEntity> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT o FROM JPAOrderEntity o LEFT JOIN FETCH o.items WHERE o.idempotencyKey = :idempotencyKey")
    Optional<JPAOrderEntity> findByIdempotencyKeyWithItems(@Param("idempotencyKey") String idempotencyKey);
}
