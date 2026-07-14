package com.ecommerce.marketplace.application.ports.in.query;

import com.ecommerce.marketplace.application.ports.out.ImportJobDetail;
import com.ecommerce.marketplace.application.ports.out.ImportRowError;
import io.vavr.collection.Seq;

/**
 * Result of {@code GetImportJobStatusUseCase}: the full {@link ImportJobDetail} of one import job
 * plus the {@link Seq} of its rejected rows (US-18). Combining both in a single immutable value lets
 * the web controller populate the status view from one use-case call, while keeping the two
 * persistence concerns (the job row and its error rows) behind separate out-ports.
 */
public record ImportJobStatusReport(ImportJobDetail detail, Seq<ImportRowError> errors) {
}
