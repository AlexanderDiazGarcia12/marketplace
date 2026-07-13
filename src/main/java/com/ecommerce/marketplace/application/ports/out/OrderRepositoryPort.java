package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Order;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import io.vavr.control.Either;
import io.vavr.control.Option;

/**
 * Output port for order persistence.
 *
 * <p>{@link #findById(OrderId)} follows the same "absence is not a failure" rule as {@link
 * ProductRepositoryPort#findBySku(com.ecommerce.marketplace.domain.model.product.SKU)}: it
 * returns {@link Option}, and the caller decides whether a missing order is an error in its own
 * context. {@link #save(Order)} can fail — e.g. a constraint violation surfaced as a {@link
 * Failure} by the adapter — hence {@code Either}.</p>
 */
public interface OrderRepositoryPort {

    Either<Failure, Order> save(Order order);

    Option<Order> findById(OrderId orderId);
}
