package com.ecommerce.marketplace.application.service;

/**
 * The seven raw, still-unparsed columns of one CSV data row
 * ({@code name,sku,description,category,price,stock,weight_kg}) as the RFC-4180 parser hands them
 * off — quotes/embedded commas already resolved, but no domain validation applied yet. A plain
 * application-layer carrier so {@link CsvProductRowValidator} stays free of any CSV-library type
 * (the {@code CSVRecord}→{@code RawProductRow} adaptation is the infrastructure consumer's job),
 * keeping validation testable without Apache Commons CSV on the classpath.
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
