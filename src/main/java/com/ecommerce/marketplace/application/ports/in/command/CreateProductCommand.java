package com.ecommerce.marketplace.application.ports.in.command;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.SKU;
import com.ecommerce.marketplace.domain.model.product.Weight;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.CreateProductUseCase},
 * carrying only domain value objects so {@code application} never sees loose primitives or web
 * DTOs. The web layer builds the value objects and accumulates failures before constructing it.
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
