package com.ecommerce.marketplace.application.ports.in.query;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Option;

/**
 * One line of an {@link OrderDetail}: the checkout snapshot enriched with the current product
 * name. {@code productName} is empty when the referenced product has since been deleted from the
 * catalog — the view falls back to showing the SKU.
 */
public record OrderLine(SKU sku, Option<String> productName, int quantity, Money unitPrice, Money subtotal) {

    public OrderLine {
        if (sku == null || productName == null || unitPrice == null || subtotal == null) {
            throw new IllegalArgumentException("OrderLine requires a sku, a (possibly empty) product name, a unit price and a subtotal");
        }
    }
}
