package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.DeleteProductUseCase;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;

/**
 * Plain-Java implementation of {@link DeleteProductUseCase} (US-12), wired via an explicit
 * {@code @Bean} in {@code infrastructure.config.SpringDependencyInjectionConfig} — no Spring
 * stereotype annotations live here, keeping the application layer framework-free.
 *
 * <p>Delegates straight to {@link ProductRepositoryPort#softDelete(SKU)}: the adapter stamps
 * {@code deleted_at} on the managed row and lets Hibernate advance {@code @Version}, so a
 * concurrent edit cannot resurrect the product. A SKU that no longer identifies a live product
 * (never existed, or already deleted) comes back as {@link Failure.ProductNotFound} — a value the
 * caller folds, never an exception.</p>
 */
public final class DeleteProductService implements DeleteProductUseCase {

    private final ProductRepositoryPort productRepository;

    public DeleteProductService(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Either<Failure, Void> deleteBySku(SKU sku) {
        return productRepository.softDelete(sku);
    }
}
