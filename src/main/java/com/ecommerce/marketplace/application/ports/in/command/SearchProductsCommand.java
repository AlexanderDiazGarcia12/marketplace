package com.ecommerce.marketplace.application.ports.in.command;

import com.ecommerce.marketplace.application.ports.query.PageRequest;
import com.ecommerce.marketplace.domain.model.product.Category;
import io.vavr.control.Option;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.SearchProductUseCase}.
 * {@code searchText} and {@code category} are optional filters modeled with {@link Option} so the
 * use case never null-checks; {@code pageRequest} bounds the result set.
 */
public record SearchProductsCommand(
        Option<String> searchText,
        Option<Category> category,
        PageRequest pageRequest
) {

    public SearchProductsCommand {
        if (searchText == null || category == null || pageRequest == null) {
            throw new IllegalArgumentException("SearchProductsCommand requires non-null (possibly empty) filters and a page request");
        }
    }
}
