package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.UpdateProductUseCase;
import com.ecommerce.marketplace.application.ports.in.command.UpdateProductCommand;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Implementation of {@link UpdateProductUseCase}. {@code Product.of(...)} re-validates every value
 * object and rebuilds the aggregate carrying the {@code expectedVersion} the editor loaded; the
 * repository then attempts the optimistic update, where Hibernate's {@code @Version} check rejects a
 * write that a concurrent edit already advanced (translated to {@link Failure.ConcurrentStockConflict}).
 */
public final class UpdateProductService implements UpdateProductUseCase {

    private final ProductRepositoryPort productRepository;

    public UpdateProductService(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Either<Failure, Product> updateProduct(UpdateProductCommand command) {
        return Product.of(
                        command.sku(),
                        command.name(),
                        command.description(),
                        command.category(),
                        command.price(),
                        command.stock(),
                        command.weight(),
                        command.expectedVersion())
                .flatMap(productRepository::update);
    }
}
