package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.domain.model.product.Product;

import java.text.DecimalFormat;

/**
 * Builds a {@link ProductForm} prefilled with a product's current values for the edit view,
 * including the optimistic {@code version} that round-trips as a hidden field, keeping the domain
 * {@link Product} out of the template. Price and weight are rendered so the fields re-parse cleanly
 * through the same value-object factories the create form uses.
 */
final class EditProductForm {

    private static final DecimalFormat WEIGHT_FORMAT = new DecimalFormat("0.###");

    private EditProductForm() {
    }

    static ProductForm from(Product product) {
        ProductForm form = new ProductForm();
        form.setSku(product.sku().value());
        form.setName(product.name());
        form.setDescription(product.description());
        form.setCategory(product.category().label());
        form.setPrice(product.price().amount().toPlainString());
        form.setStock(Integer.toString(product.stock()));
        form.setWeightKg(WEIGHT_FORMAT.format(product.weight().kilograms()));
        form.setVersion(Long.toString(product.version()));
        return form;
    }
}
