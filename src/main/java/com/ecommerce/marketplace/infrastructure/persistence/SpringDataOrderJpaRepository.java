package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository over {@code orders} (US-22). The order is always read back with its
 * line items in a single fetch join, so the mapper can rebuild the aggregate without relying on
 * lazy loading outside a transaction ({@code open-in-view} is disabled).
 *
 * <p>The admin listing reads instead through native projection queries ({@link #findSummaries}/
 * {@link #findSummariesByStatus}) that select only the header columns plus an {@code item_count}
 * correlated sub-select — never a fetch join of {@code order_items}, which under {@code LIMIT}/
 * {@code OFFSET} would force Hibernate to paginate the collection in memory ({@code HHH90003004}).
 * The paired {@code COUNT(*)} queries supply the total for page math. The
 * {@code CAST(... AS order_status)} matches the native enum type used by {@code orders.status}; the
 * two method pairs (filtered / unfiltered) avoid a nullable enum-cast parameter, mirroring the
 * split-method style in {@code SpringDataImportJobJpaRepository}.</p>
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
