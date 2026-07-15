package com.ecommerce.marketplace.application.ports.in.query;

import com.ecommerce.marketplace.application.ports.out.ImportJobDetail;
import com.ecommerce.marketplace.application.ports.out.ImportRowError;
import io.vavr.collection.Seq;

/**
 * Result of {@code GetImportJobStatusUseCase}: the full {@link ImportJobDetail} of one import job
 * plus the {@link Seq} of its rejected rows. Combining both in one immutable value lets the web
 * controller populate the status view from a single use-case call.
 */
public record ImportJobStatusReport(ImportJobDetail detail, Seq<ImportRowError> errors) {
}
