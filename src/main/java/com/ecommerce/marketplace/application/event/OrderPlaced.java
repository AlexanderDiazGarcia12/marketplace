package com.ecommerce.marketplace.application.event;

/**
 * Raised once a checkout has confirmed an order and decremented stock (US-22). Carries the order
 * identifier a consumer keys off (notifications, fulfilment), the purchased SKU, the quantity and
 * the order total. Extends US-15's {@code (orderId, sku)} stub with {@code quantity}/{@code total}
 * exactly as its Javadoc anticipated; no "buyer" field is added — the marketplace models no
 * user/customer domain concept at all in this challenge's scope, so there is no buyer to project.
 *
 * <p><strong>One event per order.</strong> {@code PurchaseCommand} is single-SKU/single-quantity,
 * so a confirmed order has exactly one line item — one {@code OrderPlaced} per order is both the
 * natural projection and consistent with the singular {@code sku} field US-15 already fixed. A
 * future multi-line cart (US-23) would extend this projection rather than emit several events, but
 * no consumer of {@code order-placed} exists yet, so this shape is verified only in isolation.</p>
 *
 * <p>{@code total} is the flat decimal string of {@code Order.totalAmount()} (e.g. {@code "59.98"}),
 * keeping the event payload primitive and free of nested domain value objects, matching the
 * {@code orderId}/{@code sku} style. It routes to the {@code order-placed} Kafka topic via
 * {@code OutboxTopics} — unchanged since US-15.</p>
 */
public record OrderPlaced(String orderId, String sku, int quantity, String total) implements DomainEvent {

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
