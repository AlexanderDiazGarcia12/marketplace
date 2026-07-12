package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.event.DomainEvent;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Output port for publishing domain events (US-15).
 *
 * <p>Implementations must write to the transactional outbox (e.g. an {@code outbox_events} row)
 * as part of the <em>same</em> business transaction that produced the event — never publish
 * straight to Kafka inline, which would reintroduce the dual-write problem the outbox pattern
 * exists to avoid. That durability guarantee is entirely an adapter concern (US-15's {@code
 * KafkaEventPublisherAdapter}); this port only fixes the contract's shape so use cases can depend
 * on it starting now.</p>
 */
public interface EventPublisherPort {

    Either<Failure, Void> publish(DomainEvent event);
}
