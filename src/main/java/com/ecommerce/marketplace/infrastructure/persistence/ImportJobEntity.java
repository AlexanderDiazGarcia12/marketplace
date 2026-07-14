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
 * JPA mapping of the {@code import_jobs} table (V5). Lives strictly inside
 * {@code infrastructure.persistence}: it never crosses into {@code domain}/{@code application} or
 * the views — {@link ImportJobMapper} builds it from the application-layer {@code NewImportJob}, and
 * only the created id is mapped back out.
 *
 * <p>Design notes tied to the physical schema:</p>
 * <ul>
 *   <li>{@code id} is a client-assigned {@code UUID} PK (generated in the application service so the
 *       use case can return it immediately), not DB- or Hibernate-generated. It is therefore an
 *       assigned identifier with no {@code @GeneratedValue}.</li>
 *   <li>{@code status} reuses the native {@code import_job_status} enum from V1 via
 *       {@code @JdbcTypeCode(NAMED_ENUM)} (binds by {@link Enum#name()}), matching the
 *       {@code products.category}/{@code outbox_events.status} convention (no mirror enum).</li>
 *   <li>The counters and {@code created_at} are DB-defaulted; {@code completed_at} is written by the
 *       US-17 worker on a terminal transition. This story only inserts a {@code PENDING} row, so it
 *       carries just the id, file reference, filename and status.</li>
 * </ul>
 *
 * <p>Accessors are package-private (Lombok {@code @Getter(PACKAGE)}) with no generic setters,
 * matching {@code JPAProductEntity}/{@code OutboxEventEntity} — the entity cannot leak out of this
 * package.</p>
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
