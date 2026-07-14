package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.collection.Seq;
import io.vavr.control.Either;

/**
 * Output port for per-row CSV validation failures (US-17), written to {@code import_job_errors}.
 *
 * <p><strong>Idempotent by construction.</strong> {@link #recordRowError} backs onto
 * {@code INSERT ... ON CONFLICT (import_job_id, row_number) DO NOTHING} over the V6 unique
 * constraint: re-recording the same rejected row on an at-least-once redelivery is a silent no-op,
 * never a duplicate. The reasons for an identical row don't change between identical retries, so the
 * first-written reasons win.</p>
 *
 * <p>{@link #countByJob} lets the worker derive {@code rejected_rows} once, at the terminal
 * transition, from the authoritative row count in the table rather than an in-memory accumulator
 * that a partial redelivery would over-count.</p>
 *
 * <p>{@link #errorsFor} is the US-18 read query: it lists every rejected row of a job, ordered by
 * {@code row_number}, with the {@code error_reason} JSONB array already deserialized back into a
 * {@code Seq<String>} of legible reasons — the exact shape {@link #recordRowError} wrote — so the
 * status view renders reasons as a list, never as raw JSON.</p>
 */
public interface ImportErrorRepositoryPort {

    Either<Failure, Void> recordRowError(ImportJobId jobId, int rowNumber, String rawLine, Seq<String> reasons);

    long countByJob(ImportJobId jobId);

    Seq<ImportRowError> errorsFor(ImportJobId jobId);
}
