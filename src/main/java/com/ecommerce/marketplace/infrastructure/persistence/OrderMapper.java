package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.out.OrderSummary;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.Order;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.order.OrderItem;
import com.ecommerce.marketplace.domain.model.order.OrderStatus;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.collection.List;
import io.vavr.collection.Seq;

import java.time.ZoneId;

/**
 * Explicit bidirectional Data Mapper between the pure {@link Order} aggregate and its
 * {@link JPAOrderEntity}/{@link JPAOrderItemEntity} persistence rows. Hand-written and
 * package-private, matching the {@code ProductMapper}/{@code ImportJobMapper}/{@code IdempotencyKeyMapper}
 * convention — MapStruct is rejected in this project because it cannot see the entities'
 * package-private accessors — so Hibernate types never cross into {@code domain} or a view.
 *
 * <p>Value objects are reconstructed through their canonical constructors ({@code new SKU(...)},
 * {@code new Money(...)}, ...) rather than their {@code Either}-returning {@code of(...)} factories:
 * a row already written through a validated {@code Order} aggregate is well-formed by construction
 * and by the DB CHECK constraints, so there is no {@code Failure} to surface on the way back.</p>
 */
final class OrderMapper {

    private OrderMapper() {
    }

    static JPAOrderEntity toEntity(Order order) {
        JPAOrderEntity entity = new JPAOrderEntity(
                order.id().value(),
                order.status(),
                order.totalAmount().amount(),
                order.paymentToken().value(),
                order.idempotencyKey().value());
        order.items().forEach(item -> entity.addItem(toItemEntity(item)));
        return entity;
    }

    static Order toDomain(JPAOrderEntity entity) {
        Seq<OrderItem> items = List.ofAll(entity.getItems()).map(OrderMapper::toDomainItem);
        return new Order(
                new OrderId(entity.getId()),
                items,
                new Money(entity.getTotalAmount()),
                entity.getStatus(),
                new PaymentToken(entity.getPaymentToken()),
                new IdempotencyKey(entity.getIdempotencyKey()));
    }

    static OrderSummary toSummary(OrderSummaryRow row) {
        return new OrderSummary(
                new OrderId(row.getId()),
                OrderStatus.valueOf(row.getStatus()),
                new Money(row.getTotalAmount()),
                row.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                row.getItemCount());
    }

    private static JPAOrderItemEntity toItemEntity(OrderItem item) {
        return new JPAOrderItemEntity(item.sku().value(), item.quantity(), item.unitPrice().amount());
    }

    private static OrderItem toDomainItem(JPAOrderItemEntity entity) {
        return new OrderItem(new SKU(entity.getProductSku()), entity.getQuantity(), new Money(entity.getUnitPrice()));
    }
}
