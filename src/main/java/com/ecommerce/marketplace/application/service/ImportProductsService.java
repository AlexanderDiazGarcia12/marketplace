package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.event.ImportRequested;
import com.ecommerce.marketplace.application.ports.in.ImportProductsUseCase;
import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.in.command.ImportProductsCommand;
import com.ecommerce.marketplace.application.ports.out.EventPublisherPort;
import com.ecommerce.marketplace.application.ports.out.ImportJobRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.NewImportJob;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Plain-Java implementation of {@link ImportProductsUseCase} (US-16), wired via an explicit
 * {@code @Bean} in {@code infrastructure.config.SpringDependencyInjectionConfig} — no Spring
 * stereotype annotations live here, keeping the application layer framework-free.
 *
 * <p>The envelope (file type/headers/size) was already validated on the web thread, which also
 * persisted the file and produced the {@code fileReference}; this use case only records the job as
 * {@code PENDING} and emits {@code ImportRequested} through the transactional outbox, then returns
 * the job id immediately. Row-by-row ingestion happens off-band in the US-17 consumer.</p>
 *
 * <p><strong>Atomicity is the caller's transaction.</strong> This service opens no transaction of
 * its own ({@code TransactionTemplate} is a Spring type, forbidden here). The {@code import_jobs}
 * insert and the outbox insert are made against the same ambient persistence context, so they are
 * atomic <em>only</em> when the caller (the web controller) wraps the whole {@code requestImport}
 * call in a transaction — exactly as {@code KafkaEventPublisherAdapter} documents. If the event
 * cannot be prepared, the {@code Either.left} short-circuits before publishing and the caller rolls
 * the whole unit of work back, so a {@code PENDING} job is never left without its event.</p>
 */
public final class ImportProductsService implements ImportProductsUseCase {

    private final ImportJobRepositoryPort importJobRepository;
    private final EventPublisherPort eventPublisher;

    public ImportProductsService(ImportJobRepositoryPort importJobRepository, EventPublisherPort eventPublisher) {
        this.importJobRepository = importJobRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Either<Failure, ImportJobId> requestImport(ImportProductsCommand command) {
        ImportJobId jobId = ImportJobId.generate();
        NewImportJob job = new NewImportJob(jobId, command.fileReference(), command.originalFilename());
        return importJobRepository.createPending(job)
                .flatMap(this::publishRequested);
    }

    private Either<Failure, ImportJobId> publishRequested(ImportJobId jobId) {
        return eventPublisher.publish(new ImportRequested(jobId.value().toString()))
                .map(published -> jobId);
    }
}
