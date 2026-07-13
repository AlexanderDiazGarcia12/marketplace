package com.ecommerce.marketplace.application.ports.in.command;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.SKU;
import com.ecommerce.marketplace.domain.model.product.Weight;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.UpdateProductUseCase}.
 *
 * <p>Like {@link CreateProductCommand} it carries only domain value objects: the web layer turns
 * raw form input into {@link SKU}, {@link Category}, {@link Money} and {@link Weight} — via their
 * {@code of(...)} factories, accumulating {@code Failure}s with Vavr {@code Validation} — before
 * this command can be constructed. It additionally carries {@code expectedVersion}: the optimistic
 * {@code @Version} the editor loaded, so a concurrent edit that already advanced the row is
 * detected as a {@link com.ecommerce.marketplace.domain.failure.Failure.ConcurrentStockConflict}
 * rather than silently overwriting the newer state (lost update).</p>
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
