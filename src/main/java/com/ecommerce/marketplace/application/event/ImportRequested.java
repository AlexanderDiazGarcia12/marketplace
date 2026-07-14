package com.ecommerce.marketplace.application.event;

/**
 * Raised when a CSV bulk-import job is accepted for asynchronous processing (adopted by US-16/17).
 * Keeps the web request responsive: the controller records the job and emits this event through
 * the outbox, and the actual chunked ingestion happens off the request thread once the relay
 * delivers it to Kafka. Carries the import job identifier a worker keys off; US-16/17 extend the
 * projection (source location, row count) as ingestion requires. US-15 fixes the event type and
 * aggregate binding so the outbox path is exercisable now.
 */
public record ImportRequested(String importJobId) implements DomainEvent {

    public static final String EVENT_TYPE = "ImportRequested";

    @Override
    public String aggregateType() {
        return "import-job";
    }

    @Override
    public String aggregateId() {
        return importJobId;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
