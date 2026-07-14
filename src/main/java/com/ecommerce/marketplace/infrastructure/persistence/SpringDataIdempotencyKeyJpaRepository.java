package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository over {@code idempotency_keys} (US-21).
 *
 * <p>Every access path is a direct lookup by the natural {@code key} PK, so this repository exposes
 * only two operations beyond {@code JpaRepository}'s {@code save}/{@code findById}:</p>
 * <ul>
 *   <li>{@link #completeIfInProgress} — the terminal {@code IN_PROGRESS → COMPLETED} transition, a
 *       native guarded UPDATE ({@code WHERE key = ? AND status = 'IN_PROGRESS'}). The guard is the
 *       same hardening US-17 applied to {@code markCompleted}/{@code markFailed}: a duplicate or
 *       out-of-order {@code complete()} can no longer silently overwrite a row that is already
 *       {@code COMPLETED}. Its {@code rowsAffected} tells the winning transition apart from a no-op,
 *       but the adapter deliberately ignores that distinction and re-reads the row either way — a
 *       second {@code complete()} for an already-terminal key is treated as idempotent completion
 *       (returns the existing snapshot), not a {@code Failure}.</li>
 * </ul>
 * The {@code CAST(... AS idempotency_status)} matches the native enum type. Native (not JPQL)
 * because the enum cast and the conditional {@code status} guard have no JPQL equivalent — the same
 * reason {@code SpringDataImportJobJpaRepository} uses native UPDATEs.
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
