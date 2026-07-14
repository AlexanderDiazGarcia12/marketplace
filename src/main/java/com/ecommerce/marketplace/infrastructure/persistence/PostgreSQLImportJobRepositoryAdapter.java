package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.out.ImportJobRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.NewImportJob;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import jakarta.persistence.EntityManager;

/**
 * JPA adapter for {@link ImportJobRepositoryPort} (US-16). The sole place where {@code import_jobs}
 * rows are inserted; it maps the application-layer {@link NewImportJob} to {@link ImportJobEntity}
 * via {@link ImportJobMapper} so no {@code @Entity} escapes this package.
 *
 * <p><strong>Same Unit of Work as the outbox.</strong> The insert is done through the ambient
 * {@link EntityManager#persist} — this adapter opens <em>no</em> transaction of its own and never
 * uses {@code REQUIRES_NEW}. It therefore joins whatever transaction the web controller has open,
 * the very same one {@code KafkaEventPublisherAdapter} joins for its outbox insert. That is what
 * makes the {@code import_jobs} row and the {@code ImportRequested} outbox row atomic: a rollback
 * reverts both, so an accepted upload can never leave a {@code PENDING} job with no event to drive
 * it. The id is client-assigned (generated in the application service), so it is simply echoed back
 * — no DB round-trip is needed to learn it.</p>
 */
public final class PostgreSQLImportJobRepositoryAdapter implements ImportJobRepositoryPort {

    private final EntityManager entityManager;

    public PostgreSQLImportJobRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Either<Failure, ImportJobId> createPending(NewImportJob job) {
        entityManager.persist(ImportJobMapper.toEntity(job));
        return Either.right(job.id());
    }
}
