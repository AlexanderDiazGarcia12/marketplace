package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.SearchProductsCommand;
import com.ecommerce.marketplace.application.ports.in.query.Page;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Input port: paginated free-text/category product search (US-13).
 *
 * <p>Returns {@code Either<Failure, Page<Product>>} with {@link Page} as an
 * application-owned Record — never {@code org.springframework.data.domain.Page} — so this
 * contract stays free of Spring, per US-13's explicit CA and US-04's zero-framework rule.
 * Failure is reserved for genuine error conditions (e.g. an invalid page request slipping past
 * construction-time validation); an empty result set is a valid {@code Page} with zero elements,
 * not a {@code Failure}.</p>
 */
public interface SearchProductUseCase {

    Either<Failure, Page<Product>> search(SearchProductsCommand command);
}
