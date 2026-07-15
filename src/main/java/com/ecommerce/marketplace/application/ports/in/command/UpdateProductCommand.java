package com.ecommerce.marketplace.application.ports.in.command;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.SKU;
import com.ecommerce.marketplace.domain.model.product.Weight;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.UpdateProductUseCase}.
 * Like {@link CreateProductCommand} it carries only domain value objects, plus {@code
 * expectedVersion}: the optimistic version the editor loaded, so a concurrent edit is detected as
 * a {@link com.ecommerce.marketplace.domain.failure.Failure.ConcurrentStockConflict} instead of a
 * silent lost update.
 */
public record UpdateProductCommand(
        SKU sku,
        String name,
        String description,
        Category category,
        Money price,
        int stock,
        Weight weight,
        long expectedVersion
) {

    public UpdateProductCommand {
        if (sku == null || name == null || category == null || price == null || weight == null) {
            throw new IllegalArgumentException("UpdateProductCommand requires a sku, a name, a category, a price and a weight");
        }
    }
}
