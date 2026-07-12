package com.ecommerce.marketplace.domain.model.order;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Try;

public record PaymentToken(String value) {

    private static final int MAX_LENGTH = 255;

    public PaymentToken {
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("PaymentToken must be non-empty and at most " + MAX_LENGTH + " characters");
        }
    }

    public static Either<Failure, PaymentToken> of(String raw) {
        return Try.of(() -> new PaymentToken(raw))
                .toEither()
                .mapLeft(cause -> new Failure.InvalidPaymentToken(raw));
    }
}
