package com.ecommerce.marketplace.application.event;

/**
 * Marker contract for domain/application events published through {@link
 * com.ecommerce.marketplace.application.ports.out.EventPublisherPort}.
 *
 * <p>Mirrors the shape the transactional outbox will persist (US-15's {@code outbox_events}
 * table: {@code aggregate_type}, {@code aggregate_id}, {@code event_type}, {@code topic}). The
 * payload itself is the implementing record's own fields — JSON serialization is an
 * infrastructure concern (the outbox adapter's job), never something this contract performs, so
 * no Jackson dependency leaks into {@code application}.</p>
 *
 * <p>Concrete events (e.g. {@code ProductImported}, {@code OrderPlaced}, {@code ImportRequested}
 * — the three topics named in US-15/US-16/US-17) are defined by the use cases that raise them in
 * later stories; US-04 only fixes the publishing contract.</p>
 */
public interface DomainEvent {

    /** Logical aggregate type this event belongs to, e.g. {@code "product"}, {@code "order"}. */
    String aggregateType();

    /** Identifier of the aggregate instance that raised this event, e.g. a SKU or order id. */
    String aggregateId();

    /** Discriminator for the event's schema/meaning, e.g. {@code "ProductImported"}. */
    String eventType();

    /** Destination topic, e.g. {@code "product-imported"}, {@code "order-placed"}. */
    String topic();
}
