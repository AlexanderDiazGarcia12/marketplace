package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository over {@code import_jobs}. The worker's state transitions are native
 * guarded UPDATEs (rather than load-mutate-flush) so the database arbitrates them as atomic
 * compare-and-sets: {@link #claimForProcessing} ({@code PENDING → PROCESSING}) whose
 * {@code rowsAffected} tells the single winner from every redelivery — the idempotency gate — and
 * {@link #markCompleted}/{@link #markFailed}, guarded by {@code WHERE status = 'PROCESSING'} so a
 * terminal transition only applies on top of an in-flight job. Native because the enum cast and the
 * conditional status guards have no JPQL equivalent.
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
