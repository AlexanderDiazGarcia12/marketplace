package com.ecommerce.marketplace.infrastructure.messaging;

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

import java.time.OffsetDateTime;

/**
 * JPA mapping of the {@code outbox_events} table, confined to {@code infrastructure.messaging}: the
 * adapter builds it from a {@link com.ecommerce.marketplace.application.event.DomainEvent} and the
 * relay reads it back through package-private accessors only.
 *
 * <p>Schema-tied notes: {@code status} binds to the native {@code outbox_status} enum via
 * {@code @JdbcTypeCode(NAMED_ENUM)}; {@code payload} is {@code JSONB}; {@code created_at} is
 * DB-defaulted ({@code insertable = false}) and {@code processed_at} is written by the relay on a
 * terminal transition. Accessors are package-private with no generic setters — the relay mutates
 * only through {@link #markPublished(OffsetDateTime)} and
 * {@link #markRetriedOrFailed(OutboxStatus, OffsetDateTime)}.</p>
 */
@Entity
@Table(name = "outbox_events")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "outbox_status")
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    OutboxEventEntity(
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    /**
     * Terminal success: stamps {@code processed_at} and flips status to {@code PUBLISHED} on the
     * managed row, so the relay's dirty-checking flush persists the transition.
     */
    void markPublished(OffsetDateTime processedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.processedAt = processedAt;
    }

    /**
     * Publish attempt failed: bumps {@code retry_count} and sets the new status — {@code PENDING} to
     * leave the row eligible for the next drain, or {@code FAILED} once the retry cap is reached,
     * stamping {@code processed_at} only on the terminal {@code FAILED} transition.
     */
    void markRetriedOrFailed(OutboxStatus newStatus, OffsetDateTime processedAt) {
        this.retryCount = this.retryCount + 1;
        this.status = newStatus;
        this.processedAt = newStatus == OutboxStatus.FAILED ? processedAt : null;
    }
}
