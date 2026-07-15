package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.out.OrderSummary;

import java.time.format.DateTimeFormatter;

/**
 * Presentation-layer projection of an {@link OrderSummary} for a row of the admin order listing.
 * Mirrors {@code ProductCardView}: the read model never reaches the Thymeleaf template, and the
 * money/date labels are pre-formatted here so the view carries no formatting logic. {@code status}
 * is the enum name ({@code CONFIRMED}/{@code REJECTED}); the template maps it to a coloured badge.
 */
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
