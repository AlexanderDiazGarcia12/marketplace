package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.in.query.ImportJobStatusReport;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Read the progress and per-row errors of an asynchronous CSV import job, so an administrator can
 * watch it advance and correct rejected rows. Returns
 * {@code Either.left(Failure.ImportJobNotFound)} when no job matches the id, otherwise the job
 * detail plus its rejected rows.
 */
public interface GetImportJobStatusUseCase {

    Either<Failure, ImportJobStatusReport> statusOf(ImportJobId jobId);
}
