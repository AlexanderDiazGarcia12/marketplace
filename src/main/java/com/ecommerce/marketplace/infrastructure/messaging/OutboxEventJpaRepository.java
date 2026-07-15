package com.ecommerce.marketplace.infrastructure.messaging;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data JPA repository over {@code outbox_events}, used only by the relay to drain via
 * {@link #lockPendingBatch(Limit)}. Inserts go through the adapter's ambient {@code EntityManager}
 * (see {@code KafkaEventPublisherAdapter}), not this repository, so they join the caller's open
 * transaction.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * Claims a batch of pending rows using {@code FOR UPDATE SKIP LOCKED} so concurrent workers or
     * overlapping ticks skip rather than contend on already-locked rows. Ordered {@code created_at}
     * oldest-first, satisfied from the partial {@code idx_outbox_events_pending} index. Must run
     * inside the caller's transaction; row locks are held until commit.
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
