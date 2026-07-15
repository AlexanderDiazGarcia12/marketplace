package com.ecommerce.marketplace.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Native projection over the {@code orders} header columns plus a correlated {@code item_count}
 * sub-select — avoids fetch-joining {@code order_items} under pagination.
 * {@link OrderMapper#toSummary} converts it to the application-layer {@code OrderSummary}.
 */
interface OrderSummaryRow {

    UUID getId();

    String getStatus();

    BigDecimal getTotalAmount();

    Instant getCreatedAt();

    long getItemCount();
}
