package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.in.command.ImportProductsCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Input port: kick off an asynchronous bulk CSV product import (US-16/US-17).
 *
 * <p>Deliberately does <em>not</em> return an import result or the imported products. Per
 * US-16's CA, ingestion must never block the web thread: this use case only validates the
 * upload envelope, persists the job as {@code PENDING} and publishes {@code ImportRequested} via
 * the transactional outbox in the same transaction as the job insert — then returns immediately.
 * Row-by-row processing, upserts and per-row error capture happen later in the US-17 consumer,
 * out of band; job progress is queried separately (US-18), not through this port.</p>
 */
public interface ImportProductsUseCase {

    Either<Failure, ImportJobId> requestImport(ImportProductsCommand command);
}
