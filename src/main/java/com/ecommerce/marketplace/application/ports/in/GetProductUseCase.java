package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;

/**
 * Input port: fetch a single product's detail by its SKU (US-10).
 *
 * <p>Unlike {@code ProductRepositoryPort.findBySku}, which returns {@code Option<Product>} because
 * absence is not a failure at the persistence boundary, this use case genuinely requires the
 * product to exist: an absent product is a business outcome the caller must handle, so it is
 * surfaced as {@code Either.left(new Failure.ProductNotFound(sku))}. Soft-deleted products are
 * treated as non-existent (the repository query excludes them), so they too yield
 * {@link Failure.ProductNotFound}. The use case never throws — every outcome is a value.</p>
 */
public interface GetProductUseCase {

    Either<Failure, Product> getBySku(SKU sku);
}
