package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import io.vavr.control.Either;

/**
 * Output port for the purchase idempotency store (US-21).
 *
 * <p>State machine mandated by US-21's CA, implemented atomically by the adapter around the
 * table's natural primary key ({@code key VARCHAR(128) PRIMARY KEY} — no synthetic id, since the
 * key itself is the contention point and the PK gives sub-millisecond lookup and resolves
 * concurrent same-key races):</p>
 * <ul>
 *   <li>new key -&gt; row inserted as {@code IN_PROGRESS}, returns {@code Either.right} with the
 *       fresh record;</li>
 *   <li>existing key still {@code IN_PROGRESS} -&gt; {@code Either.left(new
 *       Failure.DuplicateOrderRequest(key))} (maps to HTTP 409 at the web edge);</li>
 *   <li>existing key {@code COMPLETED} with a matching {@code requestHash} -&gt; {@code
 *       Either.right} with the stored snapshot, so the caller can answer the retry without
 *       re-executing the purchase;</li>
 *   <li>existing key {@code COMPLETED} (or {@code IN_PROGRESS}) with a <em>different</em>
 *       {@code requestHash} -&gt; {@code Either.left(new Failure.IdempotencyKeyMismatch(key))}
 *       (maps to HTTP 422).</li>
 * </ul>
 */
public interface IdempotencyStorePort {

    Either<Failure, IdempotencyRecord> begin(IdempotencyKey key, String requestHash);

    Either<Failure, IdempotencyRecord> complete(IdempotencyKey key, String responseSnapshot);
}
