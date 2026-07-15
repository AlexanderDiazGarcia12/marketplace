package com.ecommerce.marketplace.application.ports.in.query;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.order.OrderStatus;
import io.vavr.collection.Seq;

/**
 * Read model for the admin order-detail view: the order header plus its {@link OrderLine}s
 * enriched with product names.
 */
public record OrderDetail(OrderId id, OrderStatus status, Money totalAmount, Seq<OrderLine> lines) {

    public OrderDetail {
        if (id == null || status == null || totalAmount == null || lines == null) {
            throw new IllegalArgumentException("OrderDetail requires an id, a status, a total and its lines");
        }
    }
}
