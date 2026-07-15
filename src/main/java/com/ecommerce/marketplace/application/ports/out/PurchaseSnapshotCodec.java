package com.ecommerce.marketplace.application.ports.out;

import io.vavr.control.Option;

/**
 * Output port that turns a {@link PurchaseSnapshot} into the opaque JSON string the idempotency
 * store persists, and back. The Jackson-backed adapter lives in {@code infrastructure} so no JSON
 * type leaks into the framework-free application layer. {@link #deserialize(String)} returns
 * {@link Option} rather than throwing: a corrupt or schema-drifted snapshot decodes to
 * {@link Option#none()}, which the replay path folds into a defensive failure.
 */
public interface PurchaseSnapshotCodec {

    String serialize(PurchaseSnapshot snapshot);

    Option<PurchaseSnapshot> deserialize(String snapshot);
}
