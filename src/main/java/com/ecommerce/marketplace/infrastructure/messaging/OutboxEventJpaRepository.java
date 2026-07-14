package com.ecommerce.marketplace.infrastructure.messaging;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data JPA repository over {@code outbox_events}. The relay drains through
 * {@link #lockPendingBatch(Limit)}; the outbox adapter inserts directly through the ambient
 * {@code EntityManager} (see {@code KafkaEventPublisherAdapter}), not through this repository, so
 * its insert joins whatever transaction is already open rather than one Spring Data would manage
 * itself. No other query is needed here.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * Claims a batch of pending rows for this relay worker using {@code FOR UPDATE SKIP LOCKED} so
     * concurrent workers (or overlapping scheduler ticks) never contend on the same rows: a row
     * already row-locked by another worker is skipped, not waited on. Ordered by {@code created_at}
     * to drain oldest-first, satisfied straight from the partial {@code idx_outbox_events_pending}
     * index (which is keyed on {@code created_at} and covers only {@code status = 'PENDING'} rows),
     * so no {@code Sort} node and no scan of published/failed rows.
     *
     * <p>Must run inside a transaction (the caller's {@code TransactionTemplate}); the row locks are
     * held until that transaction commits, at which point the status transitions written by the
     * relay are durable and the locks released.</p>
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    List<OutboxEventEntity> lockPendingBatch(Limit limit);
}
