package com.ecommerce.marketplace.infrastructure.messaging;

import com.ecommerce.marketplace.application.event.DomainEvent;
import com.ecommerce.marketplace.application.ports.out.EventPublisherPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import jakarta.persistence.EntityManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link EventPublisherPort} implementation that realises the transactional-outbox side of US-15:
 * {@code publish} <strong>inserts a row into {@code outbox_events}</strong> — it never talks to
 * Kafka. Delivery to Kafka is the out-of-process {@link OutboxRelayScheduler}'s job, so no dual
 * write and no network call happen inside the business transaction.
 *
 * <p><strong>Same Unit of Work.</strong> The insert is done through
 * the ambient {@link EntityManager} via {@code persist}: this adapter opens <em>no</em> transaction
 * of its own and never uses {@code REQUIRES_NEW}. When a use case calls {@code publish(...)} inside
 * its own {@code @Transactional}/{@code TransactionTemplate}, the outbox INSERT joins that same
 * transaction and shares its persistence context — so a rollback of the business operation reverts
 * the outbox row too, and a commit persists both atomically. The name {@code KafkaEventPublisher}
 * reflects the port's intent (events end up on Kafka), not an inline Kafka write.</p>
 *
 * <p>Failure handling stays functional: a Jackson serialization error (or an unmapped event type
 * with no destination topic) is returned as {@link Failure.EventPublishFailed}, never thrown across
 * the hexagon. A serialization failure here is a programming/data error, so the caller typically
 * rolls the whole business transaction back on it — exactly the atomicity the outbox guarantees.</p>
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
