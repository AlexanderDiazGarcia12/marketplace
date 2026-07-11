package com.ecommerce.marketplace.domain.model.order;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;

public record OrderItem(SKU sku, int quantity, Money unitPrice) {

    public OrderItem {
        if (sku == null || unitPrice == null || quantity <= 0) {
            throw new IllegalArgumentException("OrderItem requires a sku, a unit price and a positive quantity");
        }
    }

    public static Either<Failure, OrderItem> of(SKU sku, int quantity, Money unitPrice) {
        return quantity <= 0
                ? Either.left(new Failure.InvalidOrderQuantity(quantity))
                : Either.right(new OrderItem(sku, quantity, unitPrice));
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
