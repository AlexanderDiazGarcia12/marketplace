package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.out.ImportJobCounters;
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
 * JPA adapter for {@link ImportJobRepositoryPort} (US-16/US-17). The sole place where {@code import_jobs}
 * rows are written; maps the application-layer {@link NewImportJob} to {@link ImportJobEntity} via
 * {@link ImportJobMapper} so no {@code @Entity} escapes this package.
 *
 * <p><strong>createPending — same Unit of Work as the outbox (US-16).</strong> The insert goes
 * through the ambient {@link EntityManager#persist}: this adapter opens no transaction of its own and
 * never uses {@code REQUIRES_NEW}, so it joins the web controller's transaction, the very one
 * {@code KafkaEventPublisherAdapter} joins for its outbox insert — making the {@code import_jobs} row
 * and the {@code ImportRequested} outbox row atomic.</p>
 *
 * <p><strong>State transitions (US-17).</strong> {@code claimForProcessing}/{@code markCompleted}/
 * {@code markFailed} are native guarded UPDATEs on {@link SpringDataImportJobJpaRepository}, executed
 * inside whatever transaction the US-17 worker has open. {@code claimForProcessing} is a
 * compare-and-set ({@code PENDING → PROCESSING}) whose {@code rowsAffected} distinguishes the single
 * winning consumer from every redelivery — the idempotency gate. {@code currentState} reads the raw
 * status label straight from the row and maps it to the application-visible {@link ImportJobState}
 * (never leaking the persistence-layer {@code ImportJobStatus}).</p>
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
