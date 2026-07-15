package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository over {@code idempotency_keys}. Every access path is a direct lookup by
 * the natural {@code key} PK, so beyond {@code JpaRepository}'s {@code save}/{@code findById} it adds
 * only {@link #completeIfInProgress} — the terminal {@code IN_PROGRESS → COMPLETED} transition as a
 * native guarded UPDATE, so a duplicate or out-of-order completion cannot overwrite an already
 * {@code COMPLETED} row. Native (not JPQL) because the enum cast and the conditional status guard
 * have no JPQL equivalent.
 */
public interface SpringDataIdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, String> {

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE idempotency_keys
            SET status = CAST('COMPLETED' AS idempotency_status),
                response_snapshot = CAST(:responseSnapshot AS jsonb)
            WHERE key = :key AND status = CAST('IN_PROGRESS' AS idempotency_status)
            """,
            nativeQuery = true)
    int completeIfInProgress(@Param("key") String key, @Param("responseSnapshot") String responseSnapshot);
}
