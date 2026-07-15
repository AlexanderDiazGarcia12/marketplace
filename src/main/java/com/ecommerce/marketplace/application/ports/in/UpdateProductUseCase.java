package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.UpdateProductCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Input port: edit an existing catalog product, re-validating every invariant through
 * {@code Product.of(...)} so an edit can never store a malformed aggregate. Fails with
 * {@link Failure.ProductNotFound} when the SKU no longer identifies a live product, and with
 * {@link Failure.ConcurrentStockConflict} when a concurrent edit advanced the row past the loaded
 * {@code expectedVersion}, surfacing the lost-update hazard as a value.
 */
public interface UpdateProductUseCase {

    Either<Failure, Product> updateProduct(UpdateProductCommand command);
}
