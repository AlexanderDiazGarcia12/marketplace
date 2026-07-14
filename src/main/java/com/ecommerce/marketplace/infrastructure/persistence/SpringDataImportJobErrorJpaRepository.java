package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository over {@code import_job_errors} (US-17).
 *
 * <p>Inserts go exclusively through {@link #insertIgnoringDuplicate}, a native
 * {@code INSERT ... ON CONFLICT (import_job_id, row_number) DO NOTHING} over the V6 unique
 * constraint: re-recording the same rejected row on an at-least-once redelivery is a silent no-op,
 * so a partially-processed job that is reprocessed from the start never duplicates error rows. The
 * reasons array is passed pre-serialized as a JSON string and cast to {@code jsonb}. Native (not
 * derived/JPQL) because {@code ON CONFLICT} and the {@code ::jsonb} cast have no JPQL equivalent.</p>
 */
public interface SpringDataImportJobErrorJpaRepository extends JpaRepository<ImportJobErrorEntity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO import_job_errors (import_job_id, row_number, raw_line, error_reason)
            VALUES (:jobId, :rowNumber, :rawLine, CAST(:errorReason AS jsonb))
            ON CONFLICT (import_job_id, row_number) DO NOTHING
            """,
            nativeQuery = true)
    int insertIgnoringDuplicate(
            @Param("jobId") UUID jobId,
            @Param("rowNumber") int rowNumber,
            @Param("rawLine") String rawLine,
            @Param("errorReason") String errorReason);

    long countByImportJobId(UUID importJobId);

    @Query(value = """
            SELECT row_number, raw_line, CAST(error_reason AS text) AS reasons
            FROM import_job_errors
            WHERE import_job_id = :jobId
            ORDER BY row_number
            """,
            nativeQuery = true)
    List<ImportJobErrorRow> findErrorsByJob(@Param("jobId") UUID jobId);
}
