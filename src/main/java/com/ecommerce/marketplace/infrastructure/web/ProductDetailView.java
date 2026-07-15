package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.domain.model.product.Product;

import java.text.DecimalFormat;

/**
 * Presentation-layer projection of the {@link Product} aggregate for the detail view. Keeps the
 * domain record out of the template and pre-formats the money/weight labels so the view stays free
 * of formatting logic.
 */
record ProductDetailView(
        String sku,
        String name,
        String description,
        String category,
        String priceLabel,
        int stock,
        boolean inStock,
        String weightLabel) {

    private static final DecimalFormat WEIGHT_FORMAT = new DecimalFormat("0.###");

    static ProductDetailView from(Product product) {
        return new ProductDetailView(
                product.sku().value(),
                product.name(),
                product.description(),
                product.category().label(),
                "$" + product.price().amount().toPlainString(),
                product.stock(),
                product.stock() > 0,
                WEIGHT_FORMAT.format(product.weight().kilograms()) + " kg");
    }
}
