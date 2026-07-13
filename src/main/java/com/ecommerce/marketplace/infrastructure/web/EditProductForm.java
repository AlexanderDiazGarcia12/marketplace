package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.domain.model.product.Product;

import java.text.DecimalFormat;

/**
 * Builds a {@link ProductForm} prefilled with a product's current values for the edit view (US-11),
 * including the loaded optimistic {@code version} that round-trips as a hidden field. Keeps the
 * domain {@link Product} out of the template: the form only ever holds presentation strings.
 *
 * <p>Price is rendered as a plain amount and weight without trailing zeros so the fields re-parse
 * cleanly through the same value-object factories the create form uses.</p>
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
