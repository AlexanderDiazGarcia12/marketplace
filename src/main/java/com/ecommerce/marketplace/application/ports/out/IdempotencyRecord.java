package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;

/**
 * Snapshot of a stored idempotency entry, returned to the caller of {@link IdempotencyStorePort}
 * so a completed retry can be answered from the snapshot without re-executing business logic.
 * {@code responseSnapshot} is the already-serialized response body kept as an opaque {@code String}
 * so JSON handling stays in adapters, never in {@code application}.
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
