package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;

/**
 * Input port: logically delete a catalog product by its SKU (US-12).
 *
 * <p>Deletion is a soft delete: the row is never physically removed, only stamped with
 * {@code deleted_at}, so historical order lines can keep referencing it. Fails with
 * {@link Failure.ProductNotFound} when the SKU no longer identifies a live product — which
 * includes the case of a product already deleted, making a replayed delete idempotent-friendly
 * rather than a double delete or a distinct error. Returns {@code Either.right(null)} on success.
 * The use case never throws; every outcome is a value.</p>
 */
public interface DeleteProductUseCase {

    Either<Failure, Void> deleteBySku(SKU sku);
}
