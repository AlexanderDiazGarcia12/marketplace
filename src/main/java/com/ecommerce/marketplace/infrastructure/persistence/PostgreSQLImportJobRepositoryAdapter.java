package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.out.ImportJobCounters;
import com.ecommerce.marketplace.application.ports.out.ImportJobDetail;
import com.ecommerce.marketplace.application.ports.out.ImportJobRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ImportJobState;
import com.ecommerce.marketplace.application.ports.out.NewImportJob;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;

/**
 * JPA adapter for {@link ImportJobRepositoryPort} — the sole place where {@code import_jobs} rows are
 * written; maps {@link NewImportJob} to {@link ImportJobEntity} via {@link ImportJobMapper} so no
 * {@code @Entity} escapes this package.
 *
 * <p>{@code createPending} inserts through the ambient {@link EntityManager#persist}, opening no
 * transaction of its own, so it joins the web controller's transaction — the same one the outbox
 * insert joins, making the {@code import_jobs} row and the {@code ImportRequested} outbox row atomic.
 * The state transitions are native guarded UPDATEs run inside the worker's transaction;
 * {@code claimForProcessing} is a compare-and-set whose {@code rowsAffected} distinguishes the single
 * winning consumer from every redelivery (the idempotency gate). {@code currentState} maps the raw
 * status label to the application-visible {@link ImportJobState}, never leaking {@code ImportJobStatus}.</p>
 */
public final class PostgreSQLImportJobRepositoryAdapter implements ImportJobRepositoryPort {

    private final EntityManager entityManager;
    private final SpringDataImportJobJpaRepository jpaRepository;

    public PostgreSQLImportJobRepositoryAdapter(
            EntityManager entityManager, SpringDataImportJobJpaRepository jpaRepository) {
        this.entityManager = entityManager;
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Either<Failure, ImportJobId> createPending(NewImportJob job) {
        entityManager.persist(ImportJobMapper.toEntity(job));
        return Either.right(job.id());
    }

    @Override
    public Option<ImportJobState> currentState(ImportJobId jobId) {
        return Option.ofOptional(jpaRepository.findStatusById(jobId.value()))
                .flatMap(this::toState);
    }

    @Override
    public Option<ImportJobDetail> detail(ImportJobId jobId) {
        return Option.ofOptional(jpaRepository.findDetailById(jobId.value()))
                .map(ImportJobMapper::toDetail);
    }

    @Override
    public Option<String> fileReference(ImportJobId jobId) {
        return Option.ofOptional(jpaRepository.findFileReferenceById(jobId.value()));
    }

    @Override
    public Either<Failure, Boolean> claimForProcessing(ImportJobId jobId) {
        return Either.right(jpaRepository.claimForProcessing(jobId.value()) > 0);
    }

    @Override
    public Either<Failure, Void> markCompleted(ImportJobId jobId, ImportJobCounters counters) {
        jpaRepository.markCompleted(
                jobId.value(), counters.total(), counters.accepted(), counters.rejected(), OffsetDateTime.now());
        return Either.right(null);
    }

    @Override
    public Either<Failure, Void> markFailed(ImportJobId jobId) {
        jpaRepository.markFailed(jobId.value(), OffsetDateTime.now());
        return Either.right(null);
    }

    private Option<ImportJobState> toState(String rawStatus) {
        return Try.of(() -> ImportJobState.valueOf(rawStatus)).toOption();
    }
}
