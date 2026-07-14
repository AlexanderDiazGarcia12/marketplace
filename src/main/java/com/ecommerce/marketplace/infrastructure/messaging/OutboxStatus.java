package com.ecommerce.marketplace.infrastructure.messaging;

/**
 * Mirrors the native Postgres {@code outbox_status} enum (V1). Constant names match the DB labels
 * exactly so Hibernate's {@code @JdbcTypeCode(NAMED_ENUM)} binds by {@link Enum#name()}.
 *
 * <ul>
 *   <li>{@code PENDING} — awaiting publication; the only status the relay drains.</li>
 *   <li>{@code PUBLISHED} — delivered to Kafka; terminal success.</li>
 *   <li>{@code FAILED} — retry cap exhausted; terminal give-up, left for manual/ops replay.</li>
 * </ul>
 */
enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
