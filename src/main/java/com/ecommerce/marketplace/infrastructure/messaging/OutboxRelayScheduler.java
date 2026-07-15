package com.ecommerce.marketplace.infrastructure.messaging;

import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Drains {@code outbox_events} to Kafka out-of-process. Each scheduled tick claims a bounded
 * ({@link #BATCH_SIZE}) batch of {@code PENDING} rows with {@code FOR UPDATE SKIP LOCKED} inside one
 * short {@link TransactionTemplate} pass, so locks are held only while publishing that batch and
 * released at commit; status transitions are written by mutating the managed entity (no setters).
 *
 * <p>Publish is synchronous ({@code KafkaTemplate.send(...).get()} blocks until the broker acks), so
 * a row is marked {@code PUBLISHED} only after Kafka confirms receipt — at-least-once by
 * construction, which is why the consumer is idempotent. A failed publish bumps {@code retry_count}
 * and leaves the row {@code PENDING} until {@link #MAX_RETRIES}, after which it is parked as terminal
 * {@code FAILED} (kept in the table for ops inspection/replay) rather than retried forever. A
 * dead-letter topic or automatic replay is out of scope.</p>
 */
public final class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 5;
    private static final Duration PUBLISH_ACK_TIMEOUT = Duration.ofSeconds(10);

    private final OutboxEventJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelayScheduler(
            OutboxEventJpaRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            TransactionTemplate transactionTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${marketplace.outbox.relay.fixed-delay-ms:1000}")
    public void drainPendingEvents() {
        transactionTemplate.executeWithoutResult(status -> relayBatch());
    }

    private void relayBatch() {
        List<OutboxEventEntity> batch = outboxRepository.lockPendingBatch(Limit.of(BATCH_SIZE));
        batch.forEach(this::relayOne);
    }

    private void relayOne(OutboxEventEntity event) {
        Try.of(() -> publishToKafka(event))
                .onSuccess(ignored -> event.markPublished(OffsetDateTime.now()))
                .onFailure(cause -> parkForRetryOrFail(event, cause));
    }

    private Void publishToKafka(OutboxEventEntity event) throws Exception {
        kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                .get(PUBLISH_ACK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        return null;
    }

    private void parkForRetryOrFail(OutboxEventEntity event, Throwable cause) {
        boolean giveUp = event.getRetryCount() + 1 >= MAX_RETRIES;
        OutboxStatus nextStatus = giveUp ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        event.markRetriedOrFailed(nextStatus, OffsetDateTime.now());
        log.warn("Outbox relay failed to publish event id={} type={} topic={} (retry {}/{}, next status {}): {}",
                event.getId(), event.getEventType(), event.getTopic(),
                event.getRetryCount(), MAX_RETRIES, nextStatus, cause.getMessage());
    }
}
