package com.ecommerce.marketplace.application.ports.in.query;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Option;

/**
 * One line of an {@link OrderDetail}: the captured checkout snapshot ({@code sku}, {@code quantity},
 * {@code unitPrice}, {@code subtotal}) enriched with the current catalog product name.
 *
 * <p>{@code productName} is an {@link Option} because the referenced product may have been
 * soft-deleted since the order was placed (US-12): historical orders legitimately reference SKUs no
 * longer live, so the name is absent rather than a failure, and the presentation layer falls back to
 * showing the SKU. The line's own data always comes from the immutable order snapshot, so a missing
 * name never breaks the detail.</p>
 */
public record OrderLine(SKU sku, Option<String> productName, int quantity, Money unitPrice, Money subtotal) {

    public OrderLine {
        if (sku == null || productName == null || unitPrice == null || subtotal == null) {
            throw new IllegalArgumentException("OrderLine requires a sku, a (possibly empty) product name, a unit price and a subtotal");
        }
    }
}
