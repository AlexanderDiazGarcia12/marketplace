package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;

/**
 * The minimal shape the application layer hands {@link ImportJobRepositoryPort#createPending} to
 * open a new import job (US-16): a pre-generated {@link ImportJobId} plus the file reference and
 * original filename carried by {@code ImportProductsCommand}. The remaining {@code import_jobs}
 * columns (status, counters, timestamps) are DB-defaulted or set later by the US-17 worker, so they
 * are not part of this contract. The id is generated in the application service (not the adapter or
 * the DB) so the use case can return it immediately in the HTTP 202 response.
 */
public record NewImportJob(ImportJobId id, String fileReference, String originalFilename) {

    public NewImportJob {
        if (id == null) {
            throw new IllegalArgumentException("NewImportJob requires a non-null ImportJobId");
        }
        if (fileReference == null || fileReference.isBlank()) {
            throw new IllegalArgumentException("NewImportJob requires a non-blank fileReference");
        }
        originalFilename = originalFilename == null ? "" : originalFilename.trim();
    }
}
