package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;

/**
 * Successful charge acknowledgement returned by {@link PaymentGatewayPort#charge}.
 * {@code confirmationReference} is an opaque identifier assigned by the gateway; the application
 * layer persists/displays it as a string and never parses its format.
 */
public record PaymentConfirmation(PaymentToken paymentToken, Money amount, String confirmationReference) {

    public PaymentConfirmation {
        if (paymentToken == null || amount == null || confirmationReference == null || confirmationReference.isBlank()) {
            throw new IllegalArgumentException("PaymentConfirmation requires a payment token, an amount and a non-blank confirmation reference");
        }
    }
}
