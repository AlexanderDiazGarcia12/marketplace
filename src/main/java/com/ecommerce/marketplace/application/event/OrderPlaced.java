package com.ecommerce.marketplace.application.event;

/**
 * Raised once a checkout has confirmed an order and decremented stock (adopted by US-22). Carries
 * the order identifier a consumer keys off (notifications, fulfilment) plus the SKU purchased.
 * US-22 extends the projection (quantity, total, buyer) as its consumers require; US-15 fixes the
 * event type and aggregate binding so the outbox path is exercisable now.
 */
public record OrderPlaced(String orderId, String sku) implements DomainEvent {

    public static final String EVENT_TYPE = "OrderPlaced";

    @Override
    public String aggregateType() {
        return "order";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }
}
