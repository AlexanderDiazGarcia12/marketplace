package com.ecommerce.marketplace.domain.model.order;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.util.UUID;

public record OrderId(UUID value) {

    public OrderId {
        if (value == null) {
            throw new IllegalArgumentException("OrderId requires a non-null UUID");
        }
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }

    public static Either<Failure, OrderId> of(UUID raw) {
        return Try.of(() -> new OrderId(raw))
                .toEither()
                .mapLeft(cause -> new Failure.InvalidOrderId(String.valueOf(raw)));
    }

    public static Either<Failure, OrderId> of(String raw) {
        return Option.of(raw)
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty())
                .flatMap(OrderId::toUuid)
                .map(OrderId::new)
                .toEither(() -> new Failure.InvalidOrderId(raw));
    }

    private static Option<UUID> toUuid(String candidate) {
        return Try.of(() -> UUID.fromString(candidate)).toOption();
    }
}
