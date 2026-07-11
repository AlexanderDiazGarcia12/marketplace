package com.ecommerce.marketplace.domain.model.order;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount) {

    private static final int SCALE = 2;

    public Money {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Money requires a non-negative amount");
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public static Either<Failure, Money> of(BigDecimal raw) {
        return Try.of(() -> new Money(raw))
                .toEither()
                .mapLeft(cause -> new Failure.InvalidMoney(raw));
    }

    public static Either<Failure, Money> of(String raw) {
        return parse(raw).flatMap(Money::of);
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money multiply(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)));
    }

    private static Either<Failure, BigDecimal> parse(String raw) {
        return Option.of(raw)
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty())
                .flatMap(Money::toBigDecimal)
                .toEither(() -> new Failure.InvalidMoney(raw));
    }

    private static Option<BigDecimal> toBigDecimal(String candidate) {
        return Try.of(() -> new BigDecimal(candidate)).toOption();
    }
}
