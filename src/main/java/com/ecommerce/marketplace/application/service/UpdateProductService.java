package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.UpdateProductUseCase;
import com.ecommerce.marketplace.application.ports.in.command.UpdateProductCommand;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Plain-Java implementation of {@link UpdateProductUseCase} (US-11), wired via an explicit
 * {@code @Bean} in {@code infrastructure.config.SpringDependencyInjectionConfig} — no Spring
 * stereotype annotations live here, keeping the application layer framework-free.
 *
 * <p>{@code Product.of(...)} re-validates every value object (SKU, name, category, price, stock,
 * weight) and rebuilds the aggregate carrying the {@code expectedVersion} the editor loaded. The
 * repository then attempts the optimistic update: Hibernate's {@code @Version} check rejects the
 * write when a concurrent edit already advanced the row, which the adapter translates to
 * {@link Failure.ConcurrentStockConflict}. This service composes those outcomes as values and
 * never handles a persistence exception itself — the only {@code try/catch} for optimistic locking
 * lives in the persistence adapter.</p>
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
