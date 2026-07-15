package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.in.query.ImportJobStatusReport;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Read the progress and per-row errors of an asynchronous CSV import job (US-18), so an
 * administrator can watch it advance and correct the rejected rows.
 *
 * <p>Returns {@code Either.left(Failure.ImportJobNotFound)} when no job matches the id, and
 * {@code Either.right(ImportJobStatusReport)} — the job detail plus its rejected rows — otherwise.
 * As every use case in the project, the outcome is a {@code Failure} value, never a thrown
 * exception.</p>
 */
public interface GetImportJobStatusUseCase {

    Either<Failure, ImportJobStatusReport> statusOf(ImportJobId jobId);
}
