package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.out.IdempotencyRecord;
import com.ecommerce.marketplace.application.ports.out.IdempotencyStorePort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import io.vavr.control.Either;
import io.vavr.control.Option;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Data JPA adapter for {@link IdempotencyStorePort} (US-21). The sole place where
 * {@code idempotency_keys} rows are read/written; it maps every persistence type back to the
 * application-layer {@link IdempotencyRecord} via {@link IdempotencyKeyMapper} so no {@code @Entity}
 * escapes {@code infrastructure.persistence}.
 *
 * <p><strong>This store is a pure key-lifecycle registry — it never runs business logic.</strong>
 * It only reads/writes rows and returns an {@link Either} describing the key's state; whether to
 * re-execute the purchase, answer from the snapshot, or reject the retry is a decision the future
 * checkout caller (US-22/23) makes by pattern-matching that {@code Either}. This adapter knows
 * nothing about payments, stock or orders.</p>
 *
 * <p><strong>{@code begin(...)} — insert-first, PK arbitrates the race.</strong> The decision
 * mechanism is an unconditional INSERT of a fresh {@code IN_PROGRESS} row, catching the
 * primary-key violation when the key already exists — deliberately <em>not</em> SELECT-then-INSERT.
 * SELECT-then-INSERT has a time-of-check/time-of-use race: two concurrent requests for the same new
 * key both SELECT empty, both attempt the INSERT, and the loser hits an unhandled PK violation.
 * Insert-first lets the {@code idempotency_keys_pkey} UNIQUE B-Tree arbitrate atomically — exactly
 * one INSERT wins ({@code Either.right} with the fresh record), and the loser catches the violation
 * and falls through to the same SELECT-and-decide path any later retry takes. This mirrors
 * {@code PostgreSQLProductRepositoryAdapter.save}'s {@code DuplicateSku} detection.</p>
 *
 * <p><strong>Decision tree on an existing key</strong> (order is load-bearing, per US-21's CA):</p>
 * <ol>
 *   <li>request-hash mismatch (any stored status) &rarr;
 *       {@code Either.left(new Failure.IdempotencyKeyMismatch(key))} (HTTP 422). Checked first
 *       because a different body under the same key is a client misuse independent of timing.</li>
 *   <li>hash matches, status {@code IN_PROGRESS} &rarr;
 *       {@code Either.left(new Failure.DuplicateOrderRequest(key))} (HTTP 409): the original
 *       purchase is still in flight.</li>
 *   <li>hash matches, status {@code COMPLETED} &rarr; {@code Either.right} with the stored snapshot,
 *       so the caller answers the retry without re-executing anything.</li>
 * </ol>
 *
 * <p><strong>{@code complete(...)} — guarded terminal transition.</strong> A native
 * {@code UPDATE ... WHERE key = ? AND status = 'IN_PROGRESS'} (see
 * {@link SpringDataIdempotencyKeyJpaRepository#completeIfInProgress}). The status guard is the same
 * hardening US-17 applied to {@code markCompleted}: a duplicate or out-of-order {@code complete()}
 * cannot overwrite an already-{@code COMPLETED} snapshot. When the guard matches nothing, the row is
 * re-read and returned as-is (idempotent completion — a genuine second {@code complete()} for a key
 * that is already terminal returns the existing snapshot rather than a phantom success). Note the
 * key-vanished fallback reuses {@link Failure.DuplicateOrderRequest} rather than a dedicated
 * "not found" variant — today unreachable (nothing deletes rows yet) and deferred with the same
 * reasoning as V7's documented TTL-cleanup tech debt: add a real variant when a deleter exists.</p>
 *
 * <p><strong>Transaction boundary: {@code begin()} always commits on its own; {@code complete()}
 * joins whatever transaction the caller has open.</strong> {@code begin()}'s insert-first race
 * arbitration only works if the failed INSERT's rollback stays local: catching a PK violation on
 * ambient {@code REQUIRED} propagation still marks the *caller's* physical transaction
 * rollback-only, so even the successful-replay branch of {@code begin()} would blow up with
 * {@code UnexpectedRollbackException} at the caller's own commit — turning the happy path of a
 * checkout's idempotent replay into a 500. {@code independentTransaction} therefore runs
 * {@code insertFresh} under {@code REQUIRES_NEW}: a losing INSERT rolls back its own isolated
 * physical transaction and the caller's transaction is untouched. This also fixes two same-call
 * INSERTs sharing one Hibernate session (an in-memory {@code NonUniqueObjectException} that never
 * reaches the real constraint) since {@code REQUIRES_NEW} suspends the caller's persistence context
 * and binds a fresh one. {@code complete()} deliberately stays {@code REQUIRED} — the response
 * snapshot must commit atomically with the business transaction it is completing, or a retry could
 * replay a "completed" purchase whose actual transaction rolled back.</p>
 */
public final class PostgreSQLIdempotencyStoreAdapter implements IdempotencyStorePort {

    private static final String KEY_PRIMARY_KEY_CONSTRAINT = "idempotency_keys_pkey";

    private final SpringDataIdempotencyKeyJpaRepository jpaRepository;
    private final TransactionTemplate joiningTransaction;
    private final TransactionTemplate independentTransaction;

    public PostgreSQLIdempotencyStoreAdapter(
            SpringDataIdempotencyKeyJpaRepository jpaRepository,
            PlatformTransactionManager transactionManager) {
        this.jpaRepository = jpaRepository;
        this.joiningTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Either<Failure, IdempotencyRecord> begin(IdempotencyKey key, String requestHash) {
        try {
            return insertFresh(key, requestHash);
        } catch (DataIntegrityViolationException violation) {
            return isKeyAlreadyPresent(violation)
                    ? decideExisting(key, requestHash)
                    : rethrow(violation);
        }
    }

    private Either<Failure, IdempotencyRecord> insertFresh(IdempotencyKey key, String requestHash) {
        IdempotencyKeyEntity persisted = independentTransaction.execute(status ->
                jpaRepository.saveAndFlush(new IdempotencyKeyEntity(key.value(), requestHash)));
        return Either.right(IdempotencyKeyMapper.toRecord(persisted));
    }

    private Either<Failure, IdempotencyRecord> decideExisting(IdempotencyKey key, String requestHash) {
        return findByKey(key)
                .map(stored -> reconcile(key, requestHash, stored))
                .getOrElse(() -> Either.left(new Failure.DuplicateOrderRequest(key)));
    }

    private Either<Failure, IdempotencyRecord> reconcile(
            IdempotencyKey key, String requestHash, IdempotencyRecord stored) {
        if (!stored.requestHash().equals(requestHash)) {
            return Either.left(new Failure.IdempotencyKeyMismatch(key));
        }
        return switch (stored.status()) {
            case IN_PROGRESS -> Either.left(new Failure.DuplicateOrderRequest(key));
            case COMPLETED -> Either.right(stored);
        };
    }

    @Override
    public Either<Failure, IdempotencyRecord> complete(IdempotencyKey key, String responseSnapshot) {
        return joiningTransaction.execute(status -> applyComplete(key, responseSnapshot));
    }

    private Either<Failure, IdempotencyRecord> applyComplete(IdempotencyKey key, String responseSnapshot) {
        jpaRepository.completeIfInProgress(key.value(), responseSnapshot);
        return findByKey(key)
                .map(Either::<Failure, IdempotencyRecord>right)
                .getOrElse(() -> Either.left(new Failure.DuplicateOrderRequest(key)));
    }

    private Option<IdempotencyRecord> findByKey(IdempotencyKey key) {
        return Option.ofOptional(jpaRepository.findById(key.value()))
                .map(IdempotencyKeyMapper::toRecord);
    }

    private static boolean isKeyAlreadyPresent(DataIntegrityViolationException violation) {
        return violation.getCause() instanceof ConstraintViolationException cause
                && KEY_PRIMARY_KEY_CONSTRAINT.equalsIgnoreCase(cause.getConstraintName());
    }

    private static Either<Failure, IdempotencyRecord> rethrow(DataIntegrityViolationException violation) {
        throw violation;
    }
}
