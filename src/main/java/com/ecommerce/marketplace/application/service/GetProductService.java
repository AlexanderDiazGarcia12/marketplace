package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.GetProductUseCase;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;

/**
 * Implementation of {@link GetProductUseCase}. Turns an empty repository lookup into
 * {@code Failure.ProductNotFound}.
 */
public final class GetProductService implements GetProductUseCase {

    private final ProductRepositoryPort productRepository;

    public GetProductService(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Either<Failure, Product> getBySku(SKU sku) {
        return productRepository.findBySku(sku)
                .toEither(() -> new Failure.ProductNotFound(sku));
    }
}
