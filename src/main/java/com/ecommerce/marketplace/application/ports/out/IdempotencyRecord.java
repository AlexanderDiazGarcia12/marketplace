package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;

/**
 * Snapshot of a stored idempotency entry (US-21's {@code idempotency_keys} table), returned to
 * the caller of {@link IdempotencyStorePort} so a completed retry can be answered from the
 * snapshot without re-executing business logic.
 *
 * <p>{@code responseSnapshot} mirrors the table's {@code response_snapshot JSONB} column, kept
 * here as an opaque {@code String} (the already-serialized response body) — JSON
 * serialization/deserialization is an infrastructure concern (Jackson lives in adapters, never
 * in {@code application}), so this port only carries the raw snapshot text through.</p>
 */
public record IdempotencyRecord(
        IdempotencyKey key,
        String requestHash,
        IdempotencyStatus status,
        String responseSnapshot
) {

    public IdempotencyRecord {
        if (key == null || requestHash == null || status == null) {
            throw new IllegalArgumentException("IdempotencyRecord requires a key, a request hash and a status");
        }
    }

    public enum IdempotencyStatus {
        IN_PROGRESS,
        COMPLETED
    }
}
