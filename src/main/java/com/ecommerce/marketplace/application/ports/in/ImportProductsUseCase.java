package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.in.command.ImportProductsCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Input port: kick off an asynchronous bulk CSV product import. Ingestion must never block the web
 * thread, so this use case only validates the upload envelope, persists the job as {@code PENDING}
 * and publishes {@code ImportRequested} via the transactional outbox in the same transaction as
 * the job insert, then returns immediately. Row-by-row processing and progress queries happen out
 * of band, not through this port.
 */
public interface ImportProductsUseCase {

    Either<Failure, ImportJobId> requestImport(ImportProductsCommand command);
}
