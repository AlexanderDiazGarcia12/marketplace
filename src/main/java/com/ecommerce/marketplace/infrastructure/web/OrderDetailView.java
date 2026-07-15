package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.query.OrderDetail;
import com.ecommerce.marketplace.application.ports.in.query.OrderLine;

import java.util.List;

/**
 * Presentation-layer projection of an {@link OrderDetail} for the admin order-detail view. Keeps the
 * application read model out of the Thymeleaf template and pre-formats the money labels. Each line's
 * {@code productName} falls back to the SKU when the referenced product has been soft-deleted, so a
 * historical order still renders every line.
 */
record OrderDetailView(String id, String status, String totalLabel, List<OrderLineView> lines) {

    static OrderDetailView from(OrderDetail detail) {
        return new OrderDetailView(
                detail.id().value().toString(),
                detail.status().name(),
                "$" + detail.totalAmount().amount().toPlainString(),
                detail.lines().map(OrderLineView::from).asJava());
    }

    record OrderLineView(String productName, String sku, int quantity, String unitPriceLabel, String subtotalLabel) {

        static OrderLineView from(OrderLine line) {
            String sku = line.sku().value();
            return new OrderLineView(
                    line.productName().getOrElse(sku),
                    sku,
                    line.quantity(),
                    "$" + line.unitPrice().amount().toPlainString(),
                    "$" + line.subtotal().amount().toPlainString());
        }
    }
}
