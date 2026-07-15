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
 * Spring Data JPA adapter for {@link IdempotencyStorePort} — the sole place where
 * {@code idempotency_keys} rows are read or written; maps every persistence type back to the
 * application-layer {@link IdempotencyRecord} via {@link IdempotencyKeyMapper} so no {@code @Entity}
 * escapes this package. It is a pure key-lifecycle registry: it returns an {@link Either} describing
 * the key's state and runs no business logic — the checkout caller decides how to react.
 *
 * <p>{@code begin(...)} does an unconditional INSERT of a fresh {@code IN_PROGRESS} row and catches
 * the PK violation when the key already exists, so the UNIQUE B-Tree arbitrates concurrent same-key
 * requests atomically (no SELECT-then-INSERT race). On an existing key the decision order is
 * load-bearing: a request-hash mismatch is a client misuse ({@link Failure.IdempotencyKeyMismatch},
 * 422); a matching hash still {@code IN_PROGRESS} means the original is in flight
 * ({@link Failure.DuplicateOrderRequest}, 409); a matching {@code COMPLETED} answers the retry from
 * the stored snapshot.</p>
 *
 * <p>{@code complete(...)} is a native guarded {@code UPDATE ... WHERE status = 'IN_PROGRESS'} (see
 * {@link SpringDataIdempotencyKeyJpaRepository#completeIfInProgress}), so a duplicate completion
 * cannot overwrite an already-{@code COMPLETED} snapshot; it re-reads and returns the row either way
 * (idempotent completion).</p>
 *
 * <p>Transaction boundaries differ by operation. {@code begin()} runs its insert under
 * {@code REQUIRES_NEW}: a losing INSERT must roll back its own isolated transaction, otherwise the PK
 * violation would mark the caller's transaction rollback-only and turn a successful idempotent replay
 * into a 500 (it also avoids two same-call INSERTs sharing one Hibernate session). {@code complete()}
 * stays {@code REQUIRED} so the snapshot commits atomically with the business transaction it
 * completes — a retry must never replay a purchase whose transaction rolled back.</p>
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
