package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;

/**
 * Input port: fetch a single product's detail by its SKU. Unlike
 * {@code ProductRepositoryPort.findBySku}, which returns {@code Option<Product>}, absence here is a
 * business outcome surfaced as {@link Failure.ProductNotFound}. Soft-deleted products are treated
 * as non-existent and yield the same failure.
 */
public interface GetProductUseCase {

    Either<Failure, Product> getBySku(SKU sku);
}
