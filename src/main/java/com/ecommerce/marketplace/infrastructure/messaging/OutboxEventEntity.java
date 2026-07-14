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
 * JPA mapping of the {@code outbox_events} table (V4). Lives strictly inside
 * {@code infrastructure.messaging}: it never crosses into {@code domain}/{@code application} or the
 * views — the outbox adapter builds it from a {@link com.ecommerce.marketplace.application.event.DomainEvent}
 * and the relay reads it back through package-private accessors only.
 *
 * <p>Design notes tied to the physical schema:</p>
 * <ul>
 *   <li>{@code id} is the synthetic identity PK ({@code BIGINT GENERATED ALWAYS AS IDENTITY}).</li>
 *   <li>{@code status} reuses the native {@code outbox_status} enum from V1 via
 *       {@code @JdbcTypeCode(NAMED_ENUM)} (binds by {@link Enum#name()}), matching the
 *       {@code products.category} convention from US-09 (no mirror enum).</li>
 *   <li>{@code payload} is {@code JSONB}; Hibernate binds a serialized JSON string to it via
 *       {@code @JdbcTypeCode(SqlTypes.JSON)}.</li>
 *   <li>{@code created_at} is DB-defaulted ({@code now()}), so it is {@code insertable = false}
 *       and read back after insert. {@code processed_at} is written by the relay on a terminal
 *       transition.</li>
 * </ul>
 *
 * <p>Accessors are package-private (Lombok {@code @Getter(PACKAGE)}) and there are no generic
 * setters — the relay mutates only through the narrow {@link #markPublished(OffsetDateTime)} and
 * {@link #markRetriedOrFailed(OutboxStatus, OffsetDateTime)} methods, keeping the mutation surface
 * closed exactly as {@code JPAProductEntity} does.</p>
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
     * Terminal success: the event was delivered to Kafka. Stamps {@code processed_at} and flips
     * status to {@code PUBLISHED} on the managed row, so the relay's dirty-checking flush persists
     * the transition.
     */
    void markPublished(OffsetDateTime processedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.processedAt = processedAt;
    }

    /**
     * Publish attempt failed: bump {@code retry_count} and set the new status. The relay passes
     * {@code PENDING} to leave the row eligible for the next drain (transient failure, retried), or
     * {@code FAILED} once the retry cap is reached (giving up), stamping {@code processed_at} only
     * on the terminal {@code FAILED} transition.
     */
    void markRetriedOrFailed(OutboxStatus newStatus, OffsetDateTime processedAt) {
        this.retryCount = this.retryCount + 1;
        this.status = newStatus;
        this.processedAt = newStatus == OutboxStatus.FAILED ? processedAt : null;
    }
}
