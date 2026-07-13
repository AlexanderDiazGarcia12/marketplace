package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.GetProductUseCase;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;

/**
 * Plain-Java implementation of {@link GetProductUseCase} (US-10), wired via an explicit
 * {@code @Bean} in {@code infrastructure.config.SpringDependencyInjectionConfig} — no Spring
 * stereotype annotations live here, keeping the application layer framework-free.
 *
 * <p>Composes the {@code Option → Either} pattern documented on {@code ProductRepositoryPort}:
 * the repository reports mere existence with {@code Option}, and this use case — which requires
 * the product to exist — turns an empty {@code Option} into {@code Failure.ProductNotFound}.</p>
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
