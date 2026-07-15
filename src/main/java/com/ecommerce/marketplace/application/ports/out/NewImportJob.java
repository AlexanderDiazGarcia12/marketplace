package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;

/**
 * The minimal shape the application layer hands {@link ImportJobRepositoryPort#createPending} to
 * open a new import job: a pre-generated {@link ImportJobId} plus the file reference and original
 * filename. The id is generated in the application service (not the adapter or DB) so the use case
 * can return it immediately in the HTTP 202 response; status, counters and timestamps are
 * DB-defaulted or set later by the worker.
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
