package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Option;

/**
 * Output port for persisting asynchronous CSV import jobs.
 *
 * <p><strong>Atomic claim (idempotency guard).</strong> {@link #claimForProcessing} is a
 * compare-and-set from {@code PENDING} to {@code PROCESSING}: it succeeds only for the single caller
 * that won the transition, failing for every redelivery/concurrent worker that finds the job past
 * {@code PENDING}, so two consumers never ingest the same file at once. The loser inspects
 * {@link #currentState} to decide whether to retry a possibly-crashed run or no-op.</p>
 *
 * <p><strong>Terminal transitions write counters once.</strong> {@link #markCompleted} stamps
 * {@code COMPLETED} with the derived {@link ImportJobCounters}; {@link #markFailed} stamps
 * {@code FAILED} for a whole-job error only, never a single rejected row (which is captured in
 * {@code import_job_errors} and leaves the job {@code COMPLETED}). Re-running either against an
 * already-terminal job rewrites the same state, so redelivery stays convergent.</p>
 */
public interface ImportJobRepositoryPort {

    Either<Failure, ImportJobId> createPending(NewImportJob job);

    Option<ImportJobState> currentState(ImportJobId jobId);

    /**
     * Full read projection for the status view: state, counters, filename and timestamps.
     * {@link Option#none()} when the job id is unknown — the use case turns that into
     * {@code Failure.ImportJobNotFound}.
     */
    Option<ImportJobDetail> detail(ImportJobId jobId);

    /**
     * The stored opaque file reference the worker re-opens to stream the CSV.
     * {@link Option#none()} when the job id is unknown.
     */
    Option<String> fileReference(ImportJobId jobId);

    Either<Failure, Boolean> claimForProcessing(ImportJobId jobId);

    Either<Failure, Void> markCompleted(ImportJobId jobId, ImportJobCounters counters);

    Either<Failure, Void> markFailed(ImportJobId jobId);
}
