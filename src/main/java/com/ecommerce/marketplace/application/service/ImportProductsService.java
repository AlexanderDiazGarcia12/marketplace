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
 * Implementation of {@link ImportProductsUseCase}. The file was already validated and persisted on
 * the web thread; this use case only records the job as {@code PENDING} and emits
 * {@code ImportRequested} through the transactional outbox, returning the job id immediately so
 * row-by-row ingestion can run off-band.
 *
 * <p>It opens no transaction of its own: the {@code import_jobs} insert and the outbox insert share
 * the caller's ambient persistence context, so they are atomic only when the web controller wraps
 * the whole {@code requestImport} call in a transaction. A failed event short-circuits before
 * publishing so a {@code PENDING} job is never left without its event.</p>
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
