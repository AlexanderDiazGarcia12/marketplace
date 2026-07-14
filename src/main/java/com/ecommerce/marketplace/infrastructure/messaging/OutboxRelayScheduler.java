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
 * Drains {@code outbox_events} to Kafka out-of-process (US-15). Runs on a fixed schedule; each tick
 * claims a bounded batch of {@code PENDING} rows with {@code FOR UPDATE SKIP LOCKED} (so parallel
 * workers/overlapping ticks never contend), publishes each to its topic, and records the outcome.
 *
 * <p><strong>Transaction scoping.</strong> The whole batch runs inside one short
 * {@link TransactionTemplate} pass: the {@code FOR UPDATE} row locks are held only for the duration
 * of publishing one bounded batch, then released at commit. Batches are kept small
 * ({@link #BATCH_SIZE}) precisely to bound how long those locks are held under bursts. The row's
 * status transition is written by mutating the managed entity (dirty-checking flush at commit), the
 * same closed-mutation pattern the product entity uses — no generic setters.</p>
 *
 * <p><strong>Publish is synchronous inside the batch.</strong> {@code KafkaTemplate.send(...).get()}
 * blocks until the broker acks, so a row is marked {@code PUBLISHED} only after Kafka confirms
 * receipt — at-least-once by construction (a crash after the ack but before commit simply re-drains
 * and re-publishes the row, which is why US-17 makes every consumer idempotent). Because it blocks,
 * this runs off the web request thread by definition (it is a scheduled task, never the UI path).</p>
 *
 * <p><strong>Retry / poison-message handling.</strong> A failed publish bumps {@code retry_count}
 * and leaves the row {@code PENDING} so the next tick retries it — until {@link #MAX_RETRIES}, after
 * which the row is parked as {@code FAILED} (terminal) rather than retried forever. This bounds the
 * blast radius of a genuinely poisoned row (e.g. an unroutable payload) without silently losing it:
 * {@code FAILED} rows stay in the table for ops inspection/replay. A dead-letter topic or an
 * automatic replay path is deliberately out of scope for US-15 and left as documented debt.</p>
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
