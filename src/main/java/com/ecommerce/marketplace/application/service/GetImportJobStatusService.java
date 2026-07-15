package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.GetImportJobStatusUseCase;
import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.in.query.ImportJobStatusReport;
import com.ecommerce.marketplace.application.ports.out.ImportErrorRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ImportJobRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Implementation of {@link GetImportJobStatusUseCase}. Turns an empty {@link ImportJobRepositoryPort#detail}
 * lookup into {@code Failure.ImportJobNotFound}; when the job exists it reads its (possibly empty)
 * rejected rows and combines both into an {@link ImportJobStatusReport}.
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
