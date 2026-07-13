package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.PurchaseCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Order;
import io.vavr.control.Either;

/**
 * Input port: transactional checkout (US-22).
 *
 * <p>This is US-04's mandated reference signature: {@code Either<Failure, Order> purchase
 * (PurchaseCommand command)}. The implementation (US-22) demarcates a single Unit of Work that
 * records idempotency, decreases stock, charges the payment gateway and persists the order —
 * possible failures include {@link Failure.InsufficientStock}, {@link Failure.PaymentRejected},
 * {@link Failure.DuplicateOrderRequest}, {@link Failure.ConcurrentStockConflict} and {@link
 * Failure.ProductNotFound}. None of that orchestration belongs in US-04: this is a contract-only
 * signature.</p>
 */
public interface PurchaseProductUseCase {

    Either<Failure, Order> purchase(PurchaseCommand command);
}
