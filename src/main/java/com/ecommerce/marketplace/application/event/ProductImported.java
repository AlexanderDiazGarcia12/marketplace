package com.ecommerce.marketplace.application.event;

/**
 * Raised once a product row has been persisted through the CSV bulk-import flow (adopted by US-16).
 * Carries the minimum a downstream consumer needs to react to a newly imported catalog entry
 * without re-reading the database: the SKU it can look the product up by and the import job that
 * produced it. US-16 extends the field set as its projection requires; US-15 fixes the event type
 * and the {@code product} aggregate binding so the outbox path is exercisable now.
 */
public record ProductImported(String sku, String importJobId) implements DomainEvent {

    public static final String EVENT_TYPE = "ProductImported";

    @Override
    public String aggregateType() {
        return "product";
    }

    @Override
    public String aggregateId() {
        return sku;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
