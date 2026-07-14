package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.out.NewImportJob;

/**
 * Data Mapper from the application-layer {@link NewImportJob} to the {@link ImportJobEntity}
 * persistence row (US-16). Hand-written and package-private, matching the {@code ProductMapper}
 * convention — the entity never leaves {@code infrastructure.persistence}.
 *
 * <p>Only the create direction exists: US-16 inserts a {@code PENDING} job and hands the id back
 * from the id it was given, so there is no entity-to-domain projection to build here (status/error
 * read-back is US-18's concern).</p>
 */
final class ImportJobMapper {

    private ImportJobMapper() {
    }

    static ImportJobEntity toEntity(NewImportJob job) {
        return new ImportJobEntity(job.id().value(), job.fileReference(), job.originalFilename());
    }
}
