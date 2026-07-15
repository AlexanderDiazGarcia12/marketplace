package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.query.OrderDetail;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import io.vavr.control.Either;

/**
 * Input port: read a single order with its lines for the admin detail view.
 *
 * <p>Returns {@code Either.left(Failure.OrderNotFound)} when no order matches the id, and
 * {@code Either.right(OrderDetail)} — the order header plus its lines enriched with product names —
 * otherwise. Composes the {@code Option → Either} read pattern used across the read side, mirroring
 * {@link GetProductUseCase}.</p>
 */
public interface GetOrderUseCase {

    Either<Failure, OrderDetail> getById(OrderId orderId);
}
