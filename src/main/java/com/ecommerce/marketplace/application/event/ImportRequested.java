package com.ecommerce.marketplace.application.event;

/**
 * Raised when a CSV bulk-import job is accepted for asynchronous processing. Keeps the web request
 * responsive: the job is recorded and this event emitted through the outbox, with chunked ingestion
 * happening off the request thread once the relay delivers it to Kafka. Carries the import job
 * identifier a worker keys off.
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
