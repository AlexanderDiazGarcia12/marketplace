package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository over {@code orders}. {@code findById}/{@code findByIdempotencyKey}
 * fetch-join the line items so the aggregate rebuilds without lazy loading. The admin listing
 * instead uses native projection queries that select only header columns plus a correlated
 * {@code item_count} — never a fetch join, which under {@code LIMIT}/{@code OFFSET} would force
 * Hibernate to paginate in memory. The filtered/unfiltered method pairs avoid a nullable
 * enum-cast parameter.
 */
public interface SpringDataOrderJpaRepository extends JpaRepository<JPAOrderEntity, UUID> {

    @Query("SELECT o FROM JPAOrderEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<JPAOrderEntity> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT o FROM JPAOrderEntity o LEFT JOIN FETCH o.items WHERE o.idempotencyKey = :idempotencyKey")
    Optional<JPAOrderEntity> findByIdempotencyKeyWithItems(@Param("idempotencyKey") String idempotencyKey);

    @Query(value = """
            SELECT o.id AS id,
                   CAST(o.status AS text) AS status,
                   o.total_amount AS total_amount,
                   o.created_at AS created_at,
                   (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) AS item_count
            FROM orders o
            ORDER BY o.created_at DESC
            LIMIT :size OFFSET :offset
            """,
            nativeQuery = true)
    List<OrderSummaryRow> findSummaries(@Param("size") int size, @Param("offset") long offset);

    @Query(value = """
            SELECT o.id AS id,
                   CAST(o.status AS text) AS status,
                   o.total_amount AS total_amount,
                   o.created_at AS created_at,
                   (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) AS item_count
            FROM orders o
            WHERE o.status = CAST(:status AS order_status)
            ORDER BY o.created_at DESC
            LIMIT :size OFFSET :offset
            """,
            nativeQuery = true)
    List<OrderSummaryRow> findSummariesByStatus(
            @Param("status") String status, @Param("size") int size, @Param("offset") long offset);

    @Query(value = "SELECT COUNT(*) FROM orders", nativeQuery = true)
    long countAll();

    @Query(value = "SELECT COUNT(*) FROM orders WHERE status = CAST(:status AS order_status)", nativeQuery = true)
    long countByStatus(@Param("status") String status);
}
