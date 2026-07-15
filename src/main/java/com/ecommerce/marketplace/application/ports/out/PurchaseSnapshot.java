package com.ecommerce.marketplace.application.ports.out;

/**
 * Minimal projection of a completed checkout, stored in the idempotency key's
 * {@code response_snapshot} so a retry can be answered without re-executing the purchase. It carries
 * only the {@code orderId}: the persisted order row is the source of truth for the retry's answer,
 * re-read through {@link OrderRepositoryPort#findById}, so the snapshot never duplicates its fields.
 * Serialization is handled by {@link PurchaseSnapshotCodec}, keeping Jackson out of the application
 * layer.
 */
public record PurchaseSnapshot(String orderId) {

    public PurchaseSnapshot {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("PurchaseSnapshot requires a non-blank order id");
        }
    }
}
