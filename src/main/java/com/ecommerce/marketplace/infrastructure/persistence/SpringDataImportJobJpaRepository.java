package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository over {@code import_jobs} (US-17 state transitions).
 *
 * <p>The transitions the row-by-row worker drives are expressed as native, guarded UPDATEs rather
 * than load-mutate-flush so they are atomic compare-and-sets the database arbitrates:</p>
 * <ul>
 *   <li>{@link #claimForProcessing} — {@code PENDING → PROCESSING} guarded by the current status;
 *       its {@code rowsAffected} tells the single winner apart from every redelivery/concurrent
 *       worker that finds the job already past {@code PENDING}. This is the idempotency gate that
 *       stops two consumers ingesting the same file at once.</li>
 *   <li>{@link #markCompleted}/{@link #markFailed} — write the terminal status, counters and
 *       {@code completed_at} in one statement, guarded by {@code WHERE status = 'PROCESSING'} so a
 *       terminal transition can only apply on top of an in-flight job: two concurrent workers racing
 *       to different outcomes for the same job can no longer have the second silently overwrite the
 *       first's terminal state — only the winner's write takes effect.</li>
 * </ul>
 * The {@code CAST(... AS import_job_status)} matches the native enum type. Native (not JPQL) because
 * the enum cast and the conditional {@code WHERE status = 'PENDING'} guard have no JPQL equivalent.
 */
public interface SpringDataImportJobJpaRepository extends JpaRepository<ImportJobEntity, UUID> {

    @Query(value = "SELECT status FROM import_jobs WHERE id = :jobId", nativeQuery = true)
    Optional<String> findStatusById(@Param("jobId") UUID jobId);

    @Query(value = """
            SELECT id, status, total_rows, accepted_rows, rejected_rows,
                   original_filename, created_at, completed_at
            FROM import_jobs
            WHERE id = :jobId
            """,
            nativeQuery = true)
    Optional<ImportJobDetailRow> findDetailById(@Param("jobId") UUID jobId);

    @Query(value = "SELECT file_reference FROM import_jobs WHERE id = :jobId", nativeQuery = true)
    Optional<String> findFileReferenceById(@Param("jobId") UUID jobId);

    @Modifying
    @Query(value = """
            UPDATE import_jobs
            SET status = CAST('PROCESSING' AS import_job_status)
            WHERE id = :jobId AND status = CAST('PENDING' AS import_job_status)
            """,
            nativeQuery = true)
    int claimForProcessing(@Param("jobId") UUID jobId);

    @Modifying
    @Query(value = """
            UPDATE import_jobs
            SET status = CAST('COMPLETED' AS import_job_status),
                total_rows = :totalRows,
                accepted_rows = :acceptedRows,
                rejected_rows = :rejectedRows,
                completed_at = :completedAt
            WHERE id = :jobId AND status = CAST('PROCESSING' AS import_job_status)
            """,
            nativeQuery = true)
    int markCompleted(
            @Param("jobId") UUID jobId,
            @Param("totalRows") long totalRows,
            @Param("acceptedRows") long acceptedRows,
            @Param("rejectedRows") long rejectedRows,
            @Param("completedAt") OffsetDateTime completedAt);

    @Modifying
    @Query(value = """
            UPDATE import_jobs
            SET status = CAST('FAILED' AS import_job_status),
                completed_at = :completedAt
            WHERE id = :jobId AND status = CAST('PROCESSING' AS import_job_status)
            """,
            nativeQuery = true)
    int markFailed(@Param("jobId") UUID jobId, @Param("completedAt") OffsetDateTime completedAt);
}
