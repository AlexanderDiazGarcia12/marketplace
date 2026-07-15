package com.ecommerce.marketplace.infrastructure.messaging;

import com.ecommerce.marketplace.application.event.DomainEvent;
import com.ecommerce.marketplace.application.ports.out.EventPublisherPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import jakarta.persistence.EntityManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Transactional-outbox {@link EventPublisherPort}: {@code publish} inserts a row into
 * {@code outbox_events} through the ambient {@link EntityManager} and never talks to Kafka — delivery
 * is the out-of-process {@link OutboxRelayScheduler}'s job. Because the insert joins the caller's
 * open transaction (no {@code REQUIRES_NEW}), a business rollback reverts the outbox row too and a
 * commit persists both atomically, avoiding a dual write. Serialization errors and unmapped event
 * types are returned as {@link Failure.EventPublishFailed} rather than thrown across the hexagon.
 */
public final class KafkaEventPublisherAdapter implements EventPublisherPort {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisherAdapter(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public Either<Failure, Void> publish(DomainEvent event) {
        return OutboxTopics.resolve(event.eventType())
                .<Failure>toEither(() -> new Failure.EventPublishFailed(
                        event.eventType(), "no Kafka topic mapped for event type"))
                .flatMap(topic -> serialize(event).map(payload -> newRow(event, topic, payload)))
                .map(this::persist);
    }

    private Either<Failure, String> serialize(DomainEvent event) {
        try {
            return Either.right(objectMapper.writeValueAsString(event));
        } catch (JacksonException serializationError) {
            return Either.left(new Failure.EventPublishFailed(
                    event.eventType(), "payload serialization failed: " + serializationError.getMessage()));
        }
    }

    private static OutboxEventEntity newRow(DomainEvent event, String topic, String payload) {
        return new OutboxEventEntity(
                event.aggregateType(), event.aggregateId(), event.eventType(), topic, payload);
    }

    private Void persist(OutboxEventEntity row) {
        entityManager.persist(row);
        return null;
    }
}
