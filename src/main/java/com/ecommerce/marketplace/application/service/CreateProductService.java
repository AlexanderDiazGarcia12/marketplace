package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.CreateProductUseCase;
import com.ecommerce.marketplace.application.ports.in.command.CreateProductCommand;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Implementation of {@link CreateProductUseCase}. The command already carries validated value
 * objects, so the only outcomes are a persisted {@link Product} or a {@link Failure.DuplicateSku}
 * surfaced by the repository when the SKU already exists.
 */
public final class CreateProductService implements CreateProductUseCase {

    private static final long INITIAL_VERSION = 0L;

    private final ProductRepositoryPort productRepository;

    public CreateProductService(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Either<Failure, Product> createProduct(CreateProductCommand command) {
        return Product.of(
                        command.sku(),
                        command.name(),
                        command.description(),
                        command.category(),
                        command.price(),
                        command.stock(),
                        command.weight(),
                        INITIAL_VERSION)
                .flatMap(productRepository::save);
    }
}
