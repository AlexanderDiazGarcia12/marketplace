package com.ecommerce.marketplace.application.service;

/**
 * The seven raw, still-unparsed columns of one CSV data row
 * ({@code name,sku,description,category,price,stock,weight_kg}). A plain application-layer carrier so
 * {@link CsvProductRowValidator} stays free of any CSV-library type and remains testable without a
 * CSV parser on the classpath.
 */
public record RawProductRow(
        String name,
        String sku,
        String description,
        String category,
        String price,
        String stock,
        String weightKg
) {
}
