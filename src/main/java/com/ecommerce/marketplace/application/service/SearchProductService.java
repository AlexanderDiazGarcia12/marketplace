package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.SearchProductUseCase;
import com.ecommerce.marketplace.application.ports.in.command.SearchProductsCommand;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Implementation of {@link SearchProductUseCase}. An empty result set is a valid {@link Page} with
 * zero elements, not a {@link Failure}. Forwards the command's optional filters and bounded page
 * request to the repository and relays its result.
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
