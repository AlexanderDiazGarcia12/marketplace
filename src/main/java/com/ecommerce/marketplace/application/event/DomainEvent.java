package com.ecommerce.marketplace.application.event;

/**
 * Marker contract for domain/application events published through {@link
 * com.ecommerce.marketplace.application.ports.out.EventPublisherPort}.
 *
 * <p>Mirrors the shape the transactional outbox persists (US-15's {@code outbox_events} table:
 * {@code aggregate_type}, {@code aggregate_id}, {@code event_type}). The payload itself is the
 * implementing record's own fields — JSON serialization is an infrastructure concern (the outbox
 * adapter's job), never something this contract performs, so no Jackson dependency leaks into
 * {@code application}.</p>
 *
 * <p><strong>US-15 decision — no {@code topic()} on this contract.</strong> The Kafka topic is a
 * message-routing detail of the infrastructure transport, not a property of the business event,
 * so it does not belong in the application contract (this resolves the recommendation the US-04
 * audit deferred to US-15). The
 * outbox adapter derives the destination topic from {@link #eventType()} via a single
 * {@code eventType → topic} mapping it owns ({@code OutboxTopics}); the domain/application layers
 * never name a Kafka topic. Adding a new event therefore means adding one mapping entry in
 * infrastructure, not touching this interface.</p>
 *
 * <p>The three concrete events named by the CA ({@code ProductImported}, {@code OrderPlaced},
 * {@code ImportRequested}) live alongside this marker. US-15 defines them with a minimal field
 * set so the outbox adapter and relay can be exercised end-to-end; the use cases that actually
 * raise them (US-16/17/22) adopt and extend them without changing this contract.</p>
 */
public interface DomainEvent {

    /** Logical aggregate type this event belongs to, e.g. {@code "product"}, {@code "order"}. */
    String aggregateType();

    /** Identifier of the aggregate instance that raised this event, e.g. a SKU or order id. */
    String aggregateId();

    /** Discriminator for the event's schema/meaning, e.g. {@code "ProductImported"}. */
    String eventType();
}
