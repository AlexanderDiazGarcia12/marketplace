package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.application.ports.query.PageRequest;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.Order;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.order.OrderStatus;
import io.vavr.control.Either;
import io.vavr.control.Option;

/**
 * Output port for order persistence.
 *
 * <p>{@link #findById(OrderId)} and {@link #findByIdempotencyKey(IdempotencyKey)} return
 * {@link Option}: a missing order is not a failure, the caller decides. {@link #save(Order)}
 * returns {@link Either} since persistence itself can fail.</p>
 *
 * <p>{@link #list(Option, PageRequest)} is a lightweight paginated projection for the admin
 * listing — never the full {@link Order} aggregate, and never a fetch join of its line items
 * (which would force in-memory pagination), just header data plus an item count.</p>
 */
public interface OrderRepositoryPort {

    Either<Failure, Order> save(Order order);

    Option<Order> findById(OrderId orderId);

    Option<Order> findByIdempotencyKey(IdempotencyKey idempotencyKey);

    Either<Failure, Page<OrderSummary>> list(Option<OrderStatus> status, PageRequest pageRequest);
}
