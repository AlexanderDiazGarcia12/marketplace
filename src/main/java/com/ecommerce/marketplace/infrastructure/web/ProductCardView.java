package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.domain.model.product.Product;

/**
 * Presentation-layer projection of the {@link Product} aggregate for a dashboard listing card. The
 * domain record never reaches the template and the money label is pre-formatted here so the view
 * carries no formatting logic.
 */
record ProductCardView(String name, String sku, String category, String priceLabel, int stock) {

    static ProductCardView from(Product product) {
        return new ProductCardView(
                product.name(),
                product.sku().value(),
                product.category().label(),
                "$" + product.price().amount().toPlainString(),
                product.stock());
    }
}
