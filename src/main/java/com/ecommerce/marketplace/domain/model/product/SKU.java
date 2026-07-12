package com.ecommerce.marketplace.domain.model.product;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Try;

import java.util.regex.Pattern;

public record SKU(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{2,63}$");

    public SKU {
        value = value == null ? null : value.trim();
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("SKU does not match the required format");
        }
    }

    public static Either<Failure, SKU> of(String raw) {
        return Try.of(() -> new SKU(raw))
                .toEither()
                .mapLeft(cause -> new Failure.InvalidSku(raw));
    }
}
