package com.ecommerce.marketplace.domain.model.order;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Try;

public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("IdempotencyKey must be non-empty and at most " + MAX_LENGTH + " characters");
        }
    }

    public static Either<Failure, IdempotencyKey> of(String raw) {
        return Try.of(() -> new IdempotencyKey(raw))
                .toEither()
                .mapLeft(cause -> new Failure.InvalidIdempotencyKey(raw));
    }
}
