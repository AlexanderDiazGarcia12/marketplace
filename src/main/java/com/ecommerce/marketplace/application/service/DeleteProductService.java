package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.DeleteProductUseCase;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;

/**
 * Implementation of {@link DeleteProductUseCase}. Delegates to
 * {@link ProductRepositoryPort#softDelete(SKU)}, which stamps {@code deleted_at} and advances
 * {@code @Version} so a concurrent edit cannot resurrect the product. A SKU that no longer
 * identifies a live product comes back as {@link Failure.ProductNotFound}.
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
