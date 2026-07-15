package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.out.ImportJobCounters;
import com.ecommerce.marketplace.application.ports.out.ImportJobDetail;
import com.ecommerce.marketplace.application.ports.out.ImportJobState;
import com.ecommerce.marketplace.application.ports.out.NewImportJob;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Data Mapper between the application-layer import-job shapes and their {@code import_jobs}
 * persistence rows, so the entity/projection never leaves {@code infrastructure.persistence}.
 * {@link #toEntity} builds a {@code PENDING} {@link ImportJobEntity} from a {@link NewImportJob};
 * {@link #toDetail} projects a native {@link ImportJobDetailRow} into the application-layer
 * {@link ImportJobDetail} the status view renders.
 */
final class ImportJobMapper {

    private ImportJobMapper() {
    }

    static ImportJobEntity toEntity(NewImportJob job) {
        return new ImportJobEntity(job.id().value(), job.fileReference(), job.originalFilename());
    }

    static ImportJobDetail toDetail(ImportJobDetailRow row) {
        return new ImportJobDetail(
                new ImportJobId(row.getId()),
                ImportJobState.valueOf(row.getStatus()),
                new ImportJobCounters(row.getTotalRows(), row.getAcceptedRows(), row.getRejectedRows()),
                row.getOriginalFilename(),
                atSystemOffset(row.getCreatedAt()),
                atSystemOffset(row.getCompletedAt()));
    }

    private static OffsetDateTime atSystemOffset(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
