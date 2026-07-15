package com.ecommerce.marketplace.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface-based projection over the {@code orders} header columns the admin listing
 * needs, plus a scalar {@code item_count} from a correlated sub-select. Native (not entity-backed)
 * so the listing never fetch-joins {@code order_items} — a collection fetch join under pagination
 * forces Hibernate to page in memory ({@code HHH90003004}). Confined to
 * {@code infrastructure.persistence}: {@link OrderMapper#toSummary} turns it into the
 * application-layer {@code OrderSummary} so it never crosses the hexagon boundary.
 *
 * <p>{@code created_at} is read as an {@link Instant} and converted to an {@code OffsetDateTime} by
 * the mapper, matching {@link ImportJobDetailRow}: a native projection over a {@code timestamptz}
 * column binds to {@code Instant} cleanly.</p>
 */
interface OrderSummaryRow {

    UUID getId();

    String getStatus();

    BigDecimal getTotalAmount();

    Instant getCreatedAt();

    long getItemCount();
}
