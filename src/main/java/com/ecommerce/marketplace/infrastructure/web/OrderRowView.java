package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.out.OrderSummary;

import java.time.format.DateTimeFormatter;

/** Presentation projection of an {@link OrderSummary} for a listing row, pre-formatted for display. */
record OrderRowView(String id, String status, String totalLabel, String createdAtLabel, long itemCount) {

    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static OrderRowView from(OrderSummary summary) {
        return new OrderRowView(
                summary.id().value().toString(),
                summary.status().name(),
                "$" + summary.totalAmount().amount().toPlainString(),
                summary.createdAt().format(CREATED_AT_FORMAT),
                summary.itemCount());
    }
}
