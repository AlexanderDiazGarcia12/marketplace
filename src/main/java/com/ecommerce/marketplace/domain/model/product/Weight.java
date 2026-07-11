package com.ecommerce.marketplace.domain.model.product;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Weight(BigDecimal kilograms) {

    private static final int SCALE = 3;

    public Weight {
        if (kilograms == null || kilograms.signum() < 0) {
            throw new IllegalArgumentException("Weight requires a non-negative value in kilograms");
        }
        kilograms = kilograms.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Either<Failure, Weight> of(BigDecimal raw) {
        return Try.of(() -> new Weight(raw))
                .toEither()
                .mapLeft(cause -> new Failure.InvalidWeight(raw));
    }

    public static Either<Failure, Weight> of(String raw) {
        return parse(raw).flatMap(Weight::of);
    }

    private static Either<Failure, BigDecimal> parse(String raw) {
        return Option.of(raw)
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty())
                .flatMap(Weight::toBigDecimal)
                .toEither(() -> new Failure.InvalidWeight(raw));
    }

    private static Option<BigDecimal> toBigDecimal(String candidate) {
        return Try.of(() -> new BigDecimal(candidate)).toOption();
    }
}
