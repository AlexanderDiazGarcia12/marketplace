package com.ecommerce.marketplace.domain.model.order;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;

public record Order(
        OrderId id,
        Seq<OrderItem> items,
        Money totalAmount,
        OrderStatus status,
        PaymentToken paymentToken,
        IdempotencyKey idempotencyKey
) {

    public Order {
        if (id == null || items == null || items.isEmpty()
                || totalAmount == null || status == null
                || paymentToken == null || idempotencyKey == null) {
            throw new IllegalArgumentException("Order requires an id, at least one item, a total, a status, a payment token and an idempotency key");
        }
        if (!totalAmount.equals(sumTotal(items))) {
            throw new IllegalArgumentException("Order total must equal the sum of its item subtotals");
        }
    }

    public static Either<Failure, Order> confirmed(
            OrderId id,
            Seq<OrderItem> items,
            PaymentToken paymentToken,
            IdempotencyKey idempotencyKey
    ) {
        return build(id, items, OrderStatus.CONFIRMED, paymentToken, idempotencyKey);
    }

    public static Either<Failure, Order> rejected(
            OrderId id,
            Seq<OrderItem> items,
            PaymentToken paymentToken,
            IdempotencyKey idempotencyKey
    ) {
        return build(id, items, OrderStatus.REJECTED, paymentToken, idempotencyKey);
    }

    private static Either<Failure, Order> build(
            OrderId id,
            Seq<OrderItem> items,
            OrderStatus status,
            PaymentToken paymentToken,
            IdempotencyKey idempotencyKey
    ) {
        return Option.of(items)
                .filter(lines -> !lines.isEmpty())
                .map(lines -> new Order(id, lines, sumTotal(lines), status, paymentToken, idempotencyKey))
                .toEither(() -> new Failure.EmptyOrder(id));
    }

    private static Money sumTotal(Seq<OrderItem> lines) {
        return lines.map(OrderItem::subtotal)
                .foldLeft(Money.zero(), Money::add);
    }
}
