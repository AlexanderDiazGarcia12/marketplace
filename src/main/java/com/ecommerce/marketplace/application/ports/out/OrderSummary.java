package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.order.OrderStatus;

import java.time.OffsetDateTime;

/**
 * Lightweight read projection of a single order for the admin listing view. Deliberately not the
 * {@link com.ecommerce.marketplace.domain.model.order.Order} aggregate: the listing must paginate
 * without fetch-joining {@code order_items} (that collection-join-plus-pagination combination
 * triggers Hibernate's in-memory pagination, {@code HHH90003004}), so it carries only the header
 * columns plus a scalar {@code itemCount} counted by a correlated sub-select — never the lines
 * themselves.
 *
 * <p>Read counterpart to {@link ImportJobDetail}: same "serve a view without loading the full
 * write aggregate" role, so the projection stays free of the heavier object graph the checkout
 * write path builds.</p>
 */
public record OrderSummary(
        OrderId id,
        OrderStatus status,
        Money totalAmount,
        OffsetDateTime createdAt,
        long itemCount) {
}
