package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.UpdateProductCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Input port: edit an existing catalog product, re-validating every invariant (US-11).
 *
 * <p>All value objects are re-validated through {@code Product.of(...)} before persistence, so an
 * edit can never store a malformed aggregate. Fails with {@link Failure.ProductNotFound} when the
 * SKU no longer identifies a live product, and with {@link Failure.ConcurrentStockConflict} when a
 * concurrent edit advanced the row past the {@code expectedVersion} the editor loaded — surfacing
 * the lost-update hazard as a value instead of overwriting the newer state. The use case never
 * throws; every outcome is a value.</p>
 */
public interface UpdateProductUseCase {

    Either<Failure, Product> updateProduct(UpdateProductCommand command);
}
