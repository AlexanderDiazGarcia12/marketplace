package com.ecommerce.marketplace.infrastructure.messaging;

import com.ecommerce.marketplace.application.event.ImportRequested;
import com.ecommerce.marketplace.application.event.ProductImported;
import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;
import com.ecommerce.marketplace.application.ports.out.EventPublisherPort;
import com.ecommerce.marketplace.application.ports.out.ImportErrorRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ImportJobCounters;
import com.ecommerce.marketplace.application.ports.out.ImportJobDetail;
import com.ecommerce.marketplace.application.ports.out.ImportJobRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ImportJobState;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.application.service.CsvProductRowValidator;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka consumer that performs asynchronous, idempotent CSV ingestion off the web thread, keeping
 * the upload endpoint responsive. It subscribes to {@code import-requested} (the topic the outbox
 * relay delivers {@code ImportRequested} to) with manual acknowledgement, acking the offset only
 * after the whole file reaches a terminal state — so a mid-file crash triggers a real Kafka
 * redelivery rather than a silent loss.
 *
 * <p>Three layers make a redelivery converge instead of duplicating: an atomic
 * {@code PENDING → PROCESSING} claim ({@code claimForProcessing}) that only the first delivery wins;
 * convergent writes ({@code upsertBySku} for valid rows, {@code recordRowError} with
 * {@code ON CONFLICT DO NOTHING} for invalid ones); and counters derived once at end-of-file from the
 * authoritative {@code import_job_errors} count rather than accumulated row-by-row.</p>
 *
 * <p>The file is streamed in {@link #CHUNK_SIZE}-row chunks (never fully in memory), each committed
 * in its own {@link TransactionTemplate} boundary where valid rows are upserted and emit a
 * {@code ProductImported} through the same outbox unit of work. A single bad row becomes an
 * {@code import_job_errors} row without aborting the file, which still ends {@code COMPLETED};
 * {@code FAILED} is reserved for a whole-job fault (the file cannot be opened/read, or an unexpected
 * error interrupts the pass).</p>
 */
public final class ProductImportConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductImportConsumer.class);

    private static final int CHUNK_SIZE = 200;

    private final ObjectMapper objectMapper;
    private final ImportJobRepositoryPort importJobRepository;
    private final ImportErrorRepositoryPort importErrorRepository;
    private final ProductRepositoryPort productRepository;
    private final EventPublisherPort eventPublisher;
    private final CsvProductRowValidator rowValidator;
    private final TransactionTemplate transactionTemplate;

    public ProductImportConsumer(
            ObjectMapper objectMapper,
            ImportJobRepositoryPort importJobRepository,
            ImportErrorRepositoryPort importErrorRepository,
            ProductRepositoryPort productRepository,
            EventPublisherPort eventPublisher,
            CsvProductRowValidator rowValidator,
            TransactionTemplate transactionTemplate) {
        this.objectMapper = objectMapper;
        this.importJobRepository = importJobRepository;
        this.importErrorRepository = importErrorRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.rowValidator = rowValidator;
        this.transactionTemplate = transactionTemplate;
    }

    @KafkaListener(
            topics = "import-requested",
            groupId = "${marketplace.import.consumer.group-id:product-import-worker}",
            containerFactory = "importListenerContainerFactory")
    public void onImportRequested(String payload, Acknowledgment acknowledgment) {
        parseJobId(payload)
                .onSuccess(this::processJob)
                .onFailure(cause -> log.error("Unparseable import-requested payload, acking to avoid poison-loop: {}", payload, cause));
        acknowledgment.acknowledge();
    }

    private Try<ImportJobId> parseJobId(String payload) {
        return Try.of(() -> objectMapper.readValue(payload, ImportRequested.class))
                .map(ImportRequested::importJobId)
                .map(UUID::fromString)
                .map(ImportJobId::new);
    }

    private void processJob(ImportJobId jobId) {
        if (claimed(jobId)) {
            ingest(jobId);
        } else {
            skipUnclaimable(jobId);
        }
    }

    private boolean claimed(ImportJobId jobId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                importJobRepository.claimForProcessing(jobId).getOrElse(false)));
    }

    private void skipUnclaimable(ImportJobId jobId) {
        ImportJobState state = importJobRepository.currentState(jobId).getOrElse(ImportJobState.PENDING);
        if (state == ImportJobState.PROCESSING) {
            reprocessStale(jobId);
        } else {
            log.info("Import job {} already {}, skipping redelivery", jobId.value(), state);
        }
    }

    private void reprocessStale(ImportJobId jobId) {
        log.warn("Import job {} still PROCESSING on redelivery (assumed crashed prior run); reprocessing idempotently", jobId.value());
        ingest(jobId);
    }

    private void ingest(ImportJobId jobId) {
        resolveFile(jobId)
                .andThen(file -> logProcessingStarted(jobId, file))
                .flatMap(file -> streamChunks(jobId, file))
                .onSuccess(totalRows -> complete(jobId, totalRows))
                .onFailure(cause -> failJob(jobId, cause));
    }

    private void logProcessingStarted(ImportJobId jobId, Path file) {
        log.info("CSV import audit event=processing_started jobId={} originalFilename=\"{}\" fileReference=\"{}\" startedAt={}",
                jobId.value(), originalFilenameOf(jobId), file, Instant.now());
    }

    private String originalFilenameOf(ImportJobId jobId) {
        return importJobRepository.detail(jobId)
                .map(ImportJobDetail::originalFilename)
                .getOrElse("unknown");
    }

    private Try<Path> resolveFile(ImportJobId jobId) {
        return importJobRepository.fileReference(jobId)
                .map(reference -> Try.success(Path.of(reference)))
                .getOrElse(() -> Try.failure(
                        new IllegalStateException("No file reference for import job " + jobId.value())));
    }

    private Try<Long> streamChunks(ImportJobId jobId, Path file) {
        return CsvProductRowReader.forEachChunk(file, CHUNK_SIZE, chunk -> processChunk(jobId, chunk));
    }

    private void processChunk(ImportJobId jobId, List<CsvProductRowReader.ParsedRow> chunk) {
        transactionTemplate.executeWithoutResult(status -> chunk.forEach(row -> processRow(jobId, row)));
    }

    private void processRow(ImportJobId jobId, CsvProductRowReader.ParsedRow row) {
        rowValidator.validate(row.raw()).fold(
                reasons -> importErrorRepository.recordRowError(jobId, row.rowNumber(), row.rawLine(), reasons),
                product -> upsertAndPublish(jobId, row, product));
    }

    /**
     * A row that passes field validation can still be rejected downstream — most notably
     * {@code upsertBySku} refusing to resurrect a soft-deleted SKU. That failure is recorded in
     * {@code import_job_errors} so it counts as rejected; otherwise, since
     * {@code accepted = total − rejected} is derived purely from that table, an unrecorded downstream
     * failure would inflate the accepted tally for a row nothing was written for.
     */
    private Either<Failure, Void> upsertAndPublish(ImportJobId jobId, CsvProductRowReader.ParsedRow row, Product product) {
        Either<Failure, Void> result = productRepository.upsertBySku(product)
                .flatMap(upserted -> eventPublisher.publish(new ProductImported(upserted.sku().value(), jobId.value().toString())));
        return result.fold(
                failure -> recordDownstreamFailure(jobId, row, product, failure),
                success -> result);
    }

    private Either<Failure, Void> recordDownstreamFailure(ImportJobId jobId, CsvProductRowReader.ParsedRow row, Product product, Failure failure) {
        log.warn("Import job {} row {} sku {} not upserted: {}", jobId.value(), row.rowNumber(), product.sku().value(), failure);
        return importErrorRepository.recordRowError(
                jobId, row.rowNumber(), row.rawLine(), io.vavr.collection.List.of(downstreamMessage(failure)));
    }

    private static String downstreamMessage(Failure failure) {
        return failure instanceof Failure.ProductNotFound
                ? "This SKU belongs to a deleted product; import will not resurrect it."
                : "Could not be saved: " + failure;
    }

    private void complete(ImportJobId jobId, long totalRows) {
        ImportJobCounters counters = transactionTemplate.execute(status -> {
            long rejected = importErrorRepository.countByJob(jobId);
            long accepted = totalRows - rejected;
            ImportJobCounters tally = new ImportJobCounters(totalRows, accepted, rejected);
            importJobRepository.markCompleted(jobId, tally);
            return tally;
        });
        log.info("CSV import audit event=processing_finished result=COMPLETED jobId={} originalFilename=\"{}\" total={} accepted={} rejected={} finishedAt={}",
                jobId.value(), originalFilenameOf(jobId), counters.total(), counters.accepted(), counters.rejected(), Instant.now());
    }

    private void failJob(ImportJobId jobId, Throwable cause) {
        transactionTemplate.executeWithoutResult(status -> importJobRepository.markFailed(jobId));
        log.error("CSV import audit event=processing_finished result=FAILED jobId={} originalFilename=\"{}\" finishedAt={}",
                jobId.value(), originalFilenameOf(jobId), Instant.now(), cause);
    }
}
