package com.ecommerce.marketplace.application.event;

/**
 * Raised once a checkout has confirmed an order and decremented stock. Carries the order identifier
 * a consumer keys off (notifications, fulfilment), the purchased SKU, the quantity and the order
 * total. A purchase is single-SKU/single-quantity, so a confirmed order has exactly one line item
 * and one {@code OrderPlaced} per order. {@code total} is the flat decimal string of
 * {@code Order.totalAmount()} (e.g. {@code "59.98"}), keeping the payload primitive and free of
 * nested domain value objects.
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
