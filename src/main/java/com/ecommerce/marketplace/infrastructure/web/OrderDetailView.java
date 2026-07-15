package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.query.OrderDetail;
import com.ecommerce.marketplace.application.ports.in.query.OrderLine;

import java.util.List;

/**
 * Presentation projection of an {@link OrderDetail} for the template, with pre-formatted money
 * labels. A line's product name falls back to the SKU if the product was later deleted.
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
