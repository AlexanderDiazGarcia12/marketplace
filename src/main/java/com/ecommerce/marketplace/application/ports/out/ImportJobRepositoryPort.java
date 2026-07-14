package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Option;

/**
 * Output port for persisting asynchronous CSV import jobs (US-16/US-17).
 *
 * <p>Grown incrementally, matching the {@link ProductRepositoryPort} convention: US-16 added only
 * {@link #createPending}; US-17 adds the state transitions the row-by-row worker drives. Status
 * read queries for the UI (US-18) are still deferred to their own story.</p>
 *
 * <p><strong>Atomic claim (idempotency guard).</strong> {@link #claimForProcessing} is a
 * compare-and-set from {@code PENDING} to {@code PROCESSING}: it returns {@code true} only for the
 * single caller that won the transition, {@code false} for every redelivery/concurrent worker that
 * finds the job already past {@code PENDING}. The worker uses it to avoid two consumers ingesting
 * the same file at once; the loser inspects {@link #currentState} to decide whether to retry a
 * possibly-crashed {@code PROCESSING} run or no-op on an already-terminal job.</p>
 *
 * <p><strong>Terminal transitions write counters once.</strong> {@link #markCompleted} stamps
 * {@code COMPLETED} with the idempotently-derived {@link ImportJobCounters}; {@link #markFailed}
 * stamps {@code FAILED} for a whole-job error (unreadable file, unexpected fault) — never for a
 * single rejected row, which is captured in {@code import_job_errors} and still leaves the job
 * {@code COMPLETED}. Both also set {@code completed_at}. Re-running either against an already-terminal
 * job simply rewrites the same terminal state, so a redelivery stays convergent.</p>
 */
public interface ImportJobRepositoryPort {

    Either<Failure, ImportJobId> createPending(NewImportJob job);

    Option<ImportJobState> currentState(ImportJobId jobId);

    /**
     * The stored file reference (the opaque handle US-16 wrote to {@code import_jobs.file_reference})
     * the worker re-opens to stream the CSV. {@link Option#none()} when the job id is unknown.
     */
    Option<String> fileReference(ImportJobId jobId);

    Either<Failure, Boolean> claimForProcessing(ImportJobId jobId);

    Either<Failure, Void> markCompleted(ImportJobId jobId, ImportJobCounters counters);

    Either<Failure, Void> markFailed(ImportJobId jobId);
}
