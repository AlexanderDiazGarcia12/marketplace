package com.ecommerce.marketplace.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA mapping of the {@code import_jobs} table, confined to {@code infrastructure.persistence}:
 * {@link ImportJobMapper} builds it from the application-layer {@code NewImportJob} and only the
 * created id is mapped back out. {@code id} is a client-assigned {@code UUID} PK (minted in the
 * application service so the use case can return it immediately), and {@code status} maps to the
 * native {@code import_job_status} enum by {@link Enum#name()}. The insert only ever writes a
 * {@code PENDING} row; counters and {@code completed_at} are set later by the async worker.
 */
@Entity
@Table(name = "import_jobs")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportJobEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "file_reference", nullable = false)
    private String fileReference;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "import_job_status")
    private ImportJobStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    ImportJobEntity(UUID id, String fileReference, String originalFilename) {
        this.id = id;
        this.fileReference = fileReference;
        this.originalFilename = originalFilename;
        this.status = ImportJobStatus.PENDING;
    }
}
