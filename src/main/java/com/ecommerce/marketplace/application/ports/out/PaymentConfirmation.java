package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;

/**
 * Successful charge acknowledgement returned by {@link PaymentGatewayPort#charge}.
 *
 * <p>{@code confirmationReference} is an opaque identifier assigned by the gateway (real or
 * fake); its internal format is entirely the adapter's business (US-20) — the application layer
 * treats it as an opaque string to persist/display, never parses it.</p>
 */
public record PaymentConfirmation(PaymentToken paymentToken, Money amount, String confirmationReference) {

    public PaymentConfirmation {
        if (paymentToken == null || amount == null || confirmationReference == null || confirmationReference.isBlank()) {
            throw new IllegalArgumentException("PaymentConfirmation requires a payment token, an amount and a non-blank confirmation reference");
        }
    }
}
