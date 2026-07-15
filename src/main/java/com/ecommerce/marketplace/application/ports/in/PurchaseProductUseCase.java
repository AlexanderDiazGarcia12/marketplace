package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.PurchaseCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Order;
import io.vavr.control.Either;

/**
 * Input port: transactional checkout. The implementation demarcates a single Unit of Work that
 * records idempotency, decreases stock, charges the payment gateway and persists the order.
 * Possible failures include {@link Failure.InsufficientStock}, {@link Failure.PaymentRejected},
 * {@link Failure.DuplicateOrderRequest}, {@link Failure.ConcurrentStockConflict} and
 * {@link Failure.ProductNotFound}.
 */
public interface PurchaseProductUseCase {

    Either<Failure, Order> purchase(PurchaseCommand command);
}
