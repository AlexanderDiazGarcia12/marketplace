package com.ecommerce.marketplace.application.ports.out;

import io.vavr.control.Option;

/**
 * Output port that turns a {@link PurchaseSnapshot} into the opaque JSON string the idempotency
 * store persists, and back (US-22). JSON (de)serialization is an infrastructure concern — the
 * Jackson-backed adapter lives in {@code infrastructure} exactly like {@code ProductCacheCodec} and
 * {@code KafkaEventPublisherAdapter}, so no {@code tools.jackson} type leaks into the framework-free
 * application layer.
 *
 * <p>{@link #deserialize(String)} returns {@link Option} rather than throwing: a corrupt or
 * schema-drifted snapshot decodes to {@link Option#none()} (mirroring {@code ProductCacheCodec}),
 * which the checkout replay path folds into a defensive failure instead of a poisoned value.</p>
 */
public interface PurchaseSnapshotCodec {

    String serialize(PurchaseSnapshot snapshot);

    Option<PurchaseSnapshot> deserialize(String snapshot);
}
