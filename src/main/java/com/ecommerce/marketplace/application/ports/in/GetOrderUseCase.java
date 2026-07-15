package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.query.OrderDetail;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import io.vavr.control.Either;

/**
 * Reads a single order with its lines for the admin detail view. Returns
 * {@code Failure.OrderNotFound} when no order matches the id.
 */
public interface GetOrderUseCase {

    Either<Failure, OrderDetail> getById(OrderId orderId);
}
