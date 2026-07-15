package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.out.ImportErrorRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ImportRowError;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Try;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * JPA adapter for {@link ImportErrorRepositoryPort} — the sole place where {@code import_job_errors}
 * rows are written. Inserts delegate to
 * {@link SpringDataImportJobErrorJpaRepository#insertIgnoringDuplicate}, whose
 * {@code ON CONFLICT DO NOTHING} makes re-recording the same rejected row on an at-least-once
 * redelivery a silent no-op. Accumulated reasons are serialized to a JSON array here; a
 * serialization failure surfaces as {@link Failure.InvalidCsvRow} rather than a thrown exception.
 *
 * <p>{@link #errorsFor} reads a job's rejected rows back, deserializing each {@code error_reason}
 * with the same {@link ObjectMapper} used on write; a row whose stored JSON is unreadable degrades
 * to a single reason carrying the raw text instead of failing the whole listing.</p>
 */
public final class PostgreSQLImportErrorRepositoryAdapter implements ImportErrorRepositoryPort {

    private static final TypeReference<java.util.List<String>> REASONS_TYPE = new TypeReference<>() {
    };

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

    @Override
    public Seq<ImportRowError> errorsFor(ImportJobId jobId) {
        return List.ofAll(jpaRepository.findErrorsByJob(jobId.value()))
                .map(row -> new ImportRowError(row.getRowNumber(), row.getRawLine(), deserialize(row.getReasons())));
    }

    private Seq<String> deserialize(String reasonsJson) {
        return Try.of(() -> objectMapper.readValue(reasonsJson, REASONS_TYPE))
                .map(List::<String>ofAll)
                .getOrElse(() -> List.of(reasonsJson));
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
