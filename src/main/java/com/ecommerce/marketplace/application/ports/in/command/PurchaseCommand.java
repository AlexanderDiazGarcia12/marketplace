package com.ecommerce.marketplace.application.ports.in.command;

import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import com.ecommerce.marketplace.domain.model.product.SKU;

/**
 * Input command for {@link com.ecommerce.marketplace.application.ports.in.PurchaseProductUseCase}.
 *
 * <p>Exact shape mandated by US-04's reference signature: {@code PurchaseCommand(sku, quantity,
 * paymentToken, idempotencyKey)}. Single-SKU/single-quantity per command, matching the reference
 * signature literally; a multi-line cart is a checkout-flow concern for US-22/US-23 to compose
 * from this primitive, not something US-04 needs to anticipate structurally.</p>
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
