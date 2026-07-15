package com.ecommerce.marketplace.application.ports.in.command;

import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import com.ecommerce.marketplace.domain.model.product.SKU;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.PurchaseProductUseCase}.
 * Single-SKU, single-quantity per command; a multi-line cart is a checkout-flow concern composed
 * from this primitive.
 */
public record PurchaseCommand(
        SKU sku,
        int quantity,
        PaymentToken paymentToken,
        IdempotencyKey idempotencyKey
) {

    public PurchaseCommand {
        if (sku == null || paymentToken == null || idempotencyKey == null || quantity <= 0) {
            throw new IllegalArgumentException("PurchaseCommand requires a sku, a positive quantity, a payment token and an idempotency key");
        }
    }
}
