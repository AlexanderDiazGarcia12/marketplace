package com.ecommerce.marketplace.application.ports.out;

/**
 * Minimal projection of a completed checkout, stored in the idempotency key's
 * {@code response_snapshot} so a retry can be answered without re-executing the purchase (US-22).
 *
 * <p>It carries only the {@code orderId}: the persisted order row is the single source of truth for
 * the retry's answer (its {@code status} tells a confirmed purchase apart from a recorded
 * rejection), so the snapshot never duplicates the order's fields — the replay re-reads the order
 * through {@link OrderRepositoryPort#findById}. Serialization to/from the JSONB column is an
 * infrastructure concern handled by {@link PurchaseSnapshotCodec}, keeping Jackson out of the
 * application layer.</p>
 */
public record PurchaseSnapshot(String orderId) {

    public PurchaseSnapshot {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("PurchaseSnapshot requires a non-blank order id");
        }
    }
}
