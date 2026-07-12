package com.ecommerce.marketplace.application.ports.in.command;

import com.ecommerce.marketplace.application.ports.in.query.PageRequest;
import com.ecommerce.marketplace.domain.model.product.Category;
import io.vavr.control.Option;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.SearchProductUseCase}.
 *
 * <p>{@code searchText} and {@code category} are both optional filters (US-13: free-text search
 * combined with an optional category filter), modeled with {@link Option} rather than nullable
 * fields so the use case never has to null-check. {@code pageRequest} bounds the result set
 * (US-13 CA: "tamaño de página acotado").</p>
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
