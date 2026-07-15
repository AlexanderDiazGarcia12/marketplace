package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.SearchProductsCommand;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Input port: paginated free-text/category product search. Returns {@link Page} as an
 * application-owned Record, never {@code org.springframework.data.domain.Page}, so the contract
 * stays free of Spring. An empty result set is a valid {@code Page} with zero elements, not a
 * {@link Failure}.
 */
public interface SearchProductUseCase {

    Either<Failure, Page<Product>> search(SearchProductsCommand command);
}
