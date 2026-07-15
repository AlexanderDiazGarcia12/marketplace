package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.order.OrderStatus;

import java.time.OffsetDateTime;

/**
 * Lightweight read projection of an order for the admin listing — header data plus an item
 * count, never the full {@link com.ecommerce.marketplace.domain.model.order.Order} aggregate or
 * its line items (a fetch join under pagination would force Hibernate to page in memory).
 */
public record OrderSummary(
        OrderId id,
        OrderStatus status,
        Money totalAmount,
        OffsetDateTime createdAt,
        long itemCount) {
}
