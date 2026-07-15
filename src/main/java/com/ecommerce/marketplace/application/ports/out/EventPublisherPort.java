package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.event.DomainEvent;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Output port for publishing domain events. Implementations must write to the transactional outbox
 * as part of the <em>same</em> business transaction that produced the event, never publish straight
 * to Kafka inline, which would reintroduce the dual-write problem the outbox pattern avoids.
 */
public interface EventPublisherPort {

    Either<Failure, Void> publish(DomainEvent event);
}
