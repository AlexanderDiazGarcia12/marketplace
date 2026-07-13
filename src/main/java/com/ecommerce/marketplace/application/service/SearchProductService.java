package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.SearchProductUseCase;
import com.ecommerce.marketplace.application.ports.in.command.SearchProductsCommand;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Plain-Java implementation of {@link SearchProductUseCase} (US-13), wired via an explicit
 * {@code @Bean} in {@code infrastructure.config.SpringDependencyInjectionConfig} — no Spring
 * stereotype annotations live here, keeping the application layer framework-free.
 *
 * <p>Unlike the {@code Option → Either} query pattern of {@code GetProductService}, an empty result
 * set is not a {@link Failure}: it is a valid {@link Page} with zero elements. The command's own
 * construction guarantees a bounded {@code PageRequest}, so the use case simply forwards the
 * already-optional filters to the repository and relays its {@code Either}.</p>
 */
public final class SearchProductService implements SearchProductUseCase {

    private final ProductRepositoryPort productRepository;

    public SearchProductService(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Either<Failure, Page<Product>> search(SearchProductsCommand command) {
        return productRepository.search(command.searchText(), command.category(), command.pageRequest());
    }
}
