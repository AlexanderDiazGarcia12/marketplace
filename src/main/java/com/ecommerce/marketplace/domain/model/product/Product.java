package com.ecommerce.marketplace.domain.model.product;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Money;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;

public record Product(
        SKU sku,
        String name,
        String description,
        Category category,
        Money price,
        int stock,
        Weight weight,
        long version
) {

    public Product {
        name = name == null ? null : name.trim();
        if (sku == null || category == null || price == null || weight == null
                || name == null || name.isEmpty() || stock < 0) {
            throw new IllegalArgumentException("Product requires a sku, a non-blank name, a category, a price, a weight and a non-negative stock");
        }
        description = Option.of(description).map(String::trim).getOrElse("");
    }

    public static Either<Failure, Product> of(
            SKU sku,
            String name,
            String description,
            Category category,
            Money price,
            int stock,
            Weight weight,
            long version
    ) {
        return Try.of(() -> new Product(sku, name, description, category, price, stock, weight, version))
                .toEither()
                .mapLeft(cause -> stock < 0
                        ? new Failure.InvalidStock(stock)
                        : new Failure.InvalidProductName(name));
    }

    public Either<Failure, Product> decreaseStock(int quantity) {
        return quantity <= 0
                ? Either.left(new Failure.InvalidStock(quantity))
                : quantity > stock
                        ? Either.left(new Failure.InsufficientStock(sku, quantity, stock))
                        : Either.right(withStock(stock - quantity));
    }

    private Product withStock(int newStock) {
        return new Product(sku, name, description, category, price, newStock, weight, version);
    }
}
