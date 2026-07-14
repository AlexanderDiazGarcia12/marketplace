package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.GetImportJobStatusUseCase;
import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.in.query.ImportJobStatusReport;
import com.ecommerce.marketplace.application.ports.out.ImportErrorRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ImportJobRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Plain-Java implementation of {@link GetImportJobStatusUseCase} (US-18), wired via an explicit
 * {@code @Bean} in {@code infrastructure.config.SpringDependencyInjectionConfig} — no Spring
 * stereotype annotations live here, keeping the application layer framework-free.
 *
 * <p>Composes the {@code Option → Either} read pattern: {@link ImportJobRepositoryPort#detail}
 * reports mere existence with {@code Option}, and this use case — which requires the job to exist —
 * turns an empty {@code Option} into {@code Failure.ImportJobNotFound}. Only when the job is present
 * does it read the (possibly empty) list of rejected rows and combine both into an
 * {@link ImportJobStatusReport}.</p>
 */
public final class GetImportJobStatusService implements GetImportJobStatusUseCase {

    private final ImportJobRepositoryPort importJobRepository;
    private final ImportErrorRepositoryPort importErrorRepository;

    public GetImportJobStatusService(
            ImportJobRepositoryPort importJobRepository, ImportErrorRepositoryPort importErrorRepository) {
        this.importJobRepository = importJobRepository;
        this.importErrorRepository = importErrorRepository;
    }

    @Override
    public Either<Failure, ImportJobStatusReport> statusOf(ImportJobId jobId) {
        return importJobRepository.detail(jobId)
                .toEither(() -> (Failure) new Failure.ImportJobNotFound(jobId.value().toString()))
                .map(detail -> new ImportJobStatusReport(detail, importErrorRepository.errorsFor(jobId)));
    }
}
