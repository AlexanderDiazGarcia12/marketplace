package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.out.ImportErrorRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * JPA adapter for {@link ImportErrorRepositoryPort} (US-17). The sole place where
 * {@code import_job_errors} rows are written.
 *
 * <p><strong>Idempotent insert.</strong> Delegates to
 * {@link SpringDataImportJobErrorJpaRepository#insertIgnoringDuplicate}, whose
 * {@code ON CONFLICT (import_job_id, row_number) DO NOTHING} makes re-recording the same rejected
 * row on an at-least-once redelivery a silent no-op. Runs inside the US-17 worker's chunk
 * transaction. The accumulated reasons ({@code Seq<String>}) are serialized to a JSON array string
 * here and cast to {@code jsonb} by the query, mirroring {@code import_job_errors.error_reason}. A
 * serialization failure surfaces as {@link Failure.InvalidCsvRow} rather than a thrown exception,
 * keeping the worker's flow functional.</p>
 */
public final class PostgreSQLImportErrorRepositoryAdapter implements ImportErrorRepositoryPort {

    private final SpringDataImportJobErrorJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public PostgreSQLImportErrorRepositoryAdapter(
            SpringDataImportJobErrorJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Either<Failure, Void> recordRowError(
            ImportJobId jobId, int rowNumber, String rawLine, Seq<String> reasons) {
        return serialize(rowNumber, reasons)
                .map(reasonsJson -> insert(jobId, rowNumber, rawLine, reasonsJson));
    }

    @Override
    public long countByJob(ImportJobId jobId) {
        return jpaRepository.countByImportJobId(jobId.value());
    }

    private Either<Failure, String> serialize(int rowNumber, Seq<String> reasons) {
        try {
            return Either.right(objectMapper.writeValueAsString(reasons.toJavaList()));
        } catch (JacksonException serializationError) {
            return Either.left(new Failure.InvalidCsvRow(rowNumber, reasons));
        }
    }

    private Void insert(ImportJobId jobId, int rowNumber, String rawLine, String reasonsJson) {
        jpaRepository.insertIgnoringDuplicate(jobId.value(), rowNumber, rawLine, reasonsJson);
        return null;
    }
}
