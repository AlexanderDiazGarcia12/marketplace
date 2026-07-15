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
 * persistence rows. Hand-written and package-private, matching the {@code ProductMapper}
 * convention — the entity/projection never leaves {@code infrastructure.persistence}.
 *
 * <ul>
 *   <li>{@link #toEntity} — the create direction (US-16): builds a {@code PENDING}
 *       {@link ImportJobEntity} from the {@link NewImportJob} write shape.</li>
 *   <li>{@link #toDetail} — the read direction (US-18): projects the {@link ImportJobDetailRow}
 *       native row into the application-layer {@link ImportJobDetail} the status view renders.</li>
 * </ul>
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
