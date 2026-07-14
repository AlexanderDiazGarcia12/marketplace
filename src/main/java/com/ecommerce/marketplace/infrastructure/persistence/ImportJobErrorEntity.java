package com.ecommerce.marketplace.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * JPA mapping of the {@code import_job_errors} table (V5/V6): one rejected CSV data row, keyed by
 * its 1-based line number within a job, with the accumulated validation reasons as a JSONB array.
 * Confined to {@code infrastructure.persistence} — never crosses into {@code domain}/{@code application}.
 *
 * <p>Rows are written exclusively through the native {@code INSERT ... ON CONFLICT DO NOTHING} on
 * {@link SpringDataImportJobErrorJpaRepository#insertIgnoringDuplicate}, so this mapping exists for
 * Hibernate's metamodel/{@code ddl-auto=validate} rather than for {@code persist}-based inserts; the
 * conflict-aware insert is the only path that keeps a partial redelivery from duplicating rows.
 * Accessors are package-private with no setters, matching the other entities.</p>
 */
@Entity
@Table(name = "import_job_errors")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportJobErrorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "import_job_id", nullable = false)
    private UUID importJobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "raw_line", nullable = false)
    private String rawLine;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_reason", nullable = false, columnDefinition = "jsonb")
    private String errorReason;
}
