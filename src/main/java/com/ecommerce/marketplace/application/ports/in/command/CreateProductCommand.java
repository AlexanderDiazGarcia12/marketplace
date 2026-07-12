package com.ecommerce.marketplace.application.ports.in.command;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.SKU;
import com.ecommerce.marketplace.domain.model.product.Weight;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.CreateProductUseCase}.
 *
 * <p>Carries only domain value objects (US-04 CA): the web layer is responsible for turning raw
 * form/JSON input into {@link SKU}, {@link Category}, {@link Money} and {@link Weight} — via
 * their {@code of(...)} factories, accumulating {@code Failure}s with Vavr {@code Validation} —
 * before this command can even be constructed. {@code application} never sees loose primitives
 * or web DTOs.</p>
 */
public record CreateProductCommand(
        SKU sku,
        String name,
        String description,
        Category category,
        Money price,
        int stock,
        Weight weight
) {

    public CreateProductCommand {
        if (sku == null || name == null || category == null || price == null || weight == null) {
            throw new IllegalArgumentException("CreateProductCommand requires a sku, a name, a category, a price and a weight");
        }
    }
}
