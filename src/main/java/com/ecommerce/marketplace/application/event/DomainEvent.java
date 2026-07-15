package com.ecommerce.marketplace.application.event;

/**
 * Marker contract for events published through {@link
 * com.ecommerce.marketplace.application.ports.out.EventPublisherPort}. Mirrors the columns the
 * transactional outbox persists ({@code aggregate_type}, {@code aggregate_id}, {@code event_type});
 * JSON serialization stays an infrastructure concern so no Jackson dependency leaks into
 * {@code application}. No {@code topic()} here: the destination topic is a transport routing detail
 * the outbox adapter derives from {@link #eventType()}, not a property of the business event.
 */
public interface DomainEvent {

    /** Logical aggregate type this event belongs to, e.g. {@code "product"}, {@code "order"}. */
    String aggregateType();

    /** Identifier of the aggregate instance that raised this event, e.g. a SKU or order id. */
    String aggregateId();

    /** Discriminator for the event's schema/meaning, e.g. {@code "ProductImported"}. */
    String eventType();
}
