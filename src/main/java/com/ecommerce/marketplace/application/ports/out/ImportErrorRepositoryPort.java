package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.collection.Seq;
import io.vavr.control.Either;

/**
 * Output port for per-row CSV validation failures, written to {@code import_job_errors}.
 *
 * <p><strong>Idempotent by construction.</strong> {@link #recordRowError} inserts on conflict do
 * nothing over the {@code (import_job_id, row_number)} unique constraint, so re-recording the same
 * rejected row on an at-least-once redelivery is a silent no-op. {@link #countByJob} lets the
 * worker derive {@code rejected_rows} once from the authoritative row count rather than an
 * in-memory accumulator a partial redelivery would over-count. {@link #errorsFor} lists a job's
 * rejected rows ordered by {@code row_number}, with reasons deserialized into a {@code Seq<String>}
 * so the status view renders them as a list, never raw JSON.</p>
 */
public interface ImportErrorRepositoryPort {

    Either<Failure, Void> recordRowError(ImportJobId jobId, int rowNumber, String rawLine, Seq<String> reasons);

    long countByJob(ImportJobId jobId);

    Seq<ImportRowError> errorsFor(ImportJobId jobId);
}
