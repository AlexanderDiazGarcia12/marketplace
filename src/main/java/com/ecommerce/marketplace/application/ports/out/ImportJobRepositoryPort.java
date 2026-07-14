package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Output port for persisting asynchronous CSV import jobs (US-16).
 *
 * <p>Grown incrementally, matching the {@link ProductRepositoryPort} convention: US-16 only needs
 * to create a job in {@code PENDING} the moment an upload is accepted, so that is the only method
 * declared here. Status/error queries (US-18) and the terminal-state transitions the row-by-row
 * worker performs (US-17) are added by their own stories, not speculated now.</p>
 *
 * <p>The create runs inside the caller's ambient transaction — the same one the outbox insert
 * joins — so the {@code import_jobs} row and the {@code ImportRequested} outbox row are committed
 * atomically, never leaving a {@code PENDING} job with no event to drive it.</p>
 */
public interface ImportJobRepositoryPort {

    Either<Failure, ImportJobId> createPending(NewImportJob job);
}
