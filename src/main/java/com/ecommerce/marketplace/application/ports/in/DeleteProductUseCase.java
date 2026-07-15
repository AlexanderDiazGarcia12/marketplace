package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;

/**
 * Input port: soft-delete a catalog product by its SKU. The row is stamped with {@code deleted_at}
 * rather than removed, so historical order lines keep referencing it. Fails with
 * {@link Failure.ProductNotFound} when the SKU no longer identifies a live product (including an
 * already-deleted one, making a replayed delete idempotent-friendly).
 */
public interface DeleteProductUseCase {

    Either<Failure, Void> deleteBySku(SKU sku);
}
