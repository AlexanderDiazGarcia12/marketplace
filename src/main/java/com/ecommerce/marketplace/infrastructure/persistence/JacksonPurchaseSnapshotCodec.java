package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.out.PurchaseSnapshot;
import com.ecommerce.marketplace.application.ports.out.PurchaseSnapshotCodec;
import io.vavr.control.Option;
import io.vavr.control.Try;
import tools.jackson.databind.ObjectMapper;

/**
 * Jackson-backed {@link PurchaseSnapshotCodec} (US-22). Confines JSON (de)serialization of the
 * idempotency {@code response_snapshot} to {@code infrastructure}, exactly like {@code ProductCacheCodec}
 * and {@code KafkaEventPublisherAdapter} keep {@code tools.jackson} out of the hexagon. It maps to a
 * flat, infrastructure-owned {@link SnapshotDocument} DTO of plain Java types rather than serializing
 * the application record directly, so the persisted JSON shape stays an implementation detail.
 *
 * <p>Decoding rebuilds the {@link PurchaseSnapshot} through its validating constructor; a corrupt or
 * schema-drifted document surfaces as {@link Option#none()} (the checkout replay folds that into a
 * defensive failure) rather than a thrown exception crossing the port.</p>
 */
public final class JacksonPurchaseSnapshotCodec implements PurchaseSnapshotCodec {

    private final ObjectMapper objectMapper;

    public JacksonPurchaseSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String serialize(PurchaseSnapshot snapshot) {
        return objectMapper.writeValueAsString(new SnapshotDocument(snapshot.orderId()));
    }

    @Override
    public Option<PurchaseSnapshot> deserialize(String snapshot) {
        return Try.of(() -> objectMapper.readValue(snapshot, SnapshotDocument.class))
                .toOption()
                .flatMap(SnapshotDocument::toSnapshot);
    }

    record SnapshotDocument(String orderId) {

        Option<PurchaseSnapshot> toSnapshot() {
            return Try.of(() -> new PurchaseSnapshot(orderId)).toOption();
        }
    }
}
