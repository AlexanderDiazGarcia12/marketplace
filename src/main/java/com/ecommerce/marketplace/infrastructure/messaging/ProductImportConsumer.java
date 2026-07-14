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
 * Kafka consumer that performs the asynchronous, idempotent CSV ingestion (US-17). Subscribes to
 * {@code import-requested} — the topic the outbox relay (US-15) delivers {@code ImportRequested} to —
 * and never runs on the web thread, so the upload endpoint (US-16) stays responsive (RNF-2/RNF-3).
 *
 * <p><strong>Manual ack for genuine at-least-once.</strong> The container is configured for manual
 * acknowledgement; this listener acks the offset <em>only after</em> the whole file is processed and
 * the job reaches a terminal state. A crash mid-file leaves the offset unconfirmed, so Kafka
 * redelivers the same message — a real reprocess, never a silent loss.</p>
 *
 * <p><strong>Idempotency under redelivery.</strong> Three layers make a redelivery converge instead
 * of duplicating:</p>
 * <ol>
 *   <li><strong>Atomic claim.</strong> {@code claimForProcessing} is a {@code PENDING → PROCESSING}
 *       compare-and-set. Only the first delivery wins it; a redelivery of an already-terminal job
 *       is a no-op-and-ack, and a redelivery of a job still {@code PROCESSING} (a crashed prior run)
 *       is reprocessed from the start — safe precisely because of the next two layers.</li>
 *   <li><strong>Convergent writes.</strong> Valid rows go through {@code upsertBySku} (same data on
 *       re-apply, only the write counter {@code version} advances) and invalid rows through
 *       {@code recordRowError} ({@code ON CONFLICT DO NOTHING}), so neither duplicates on reprocess.</li>
 *   <li><strong>Counters written once, derived idempotently.</strong> Tallies are never accumulated
 *       row-by-row. At end-of-file the terminal transition derives {@code rejected} from the
 *       authoritative {@code import_job_errors} count, {@code total} from rows read this pass and
 *       {@code accepted = total − rejected} — so any full redelivery lands on the same numbers.</li>
 * </ol>
 *
 * <p><strong>Chunked, per-chunk transactions.</strong> The file is streamed in {@link #CHUNK_SIZE}-row
 * chunks (never fully in memory); each chunk runs in its own {@link TransactionTemplate} boundary. In
 * that transaction, valid rows are upserted and each emits a {@code ProductImported} through the same
 * outbox (shared Unit of Work with the upsert, per US-15/16), and invalid rows are recorded — so a
 * chunk commits atomically. A single bad row never aborts the file: it becomes an
 * {@code import_job_errors} row and the job still completes.</p>
 *
 * <p><strong>COMPLETED vs FAILED.</strong> A file streamed to the end is {@code COMPLETED} even with
 * rejected rows ({@code rejected_rows > 0}). {@code FAILED} is reserved for a whole-job fault — the
 * file cannot be opened/read from its {@code fileReference}, or an unexpected error interrupts the
 * pass — not for per-row validation.</p>
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
     * {@code upsertBySku} refusing to resurrect a soft-deleted SKU (US-12/US-17). That failure must
     * count as rejected, not silently as accepted: {@code accepted = total − rejected} is derived
     * purely from {@code import_job_errors}, so any downstream failure that isn't recorded there
     * would inflate the accepted tally for a row nothing was actually written for.
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
