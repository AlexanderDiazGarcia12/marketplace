package com.ecommerce.marketplace.infrastructure.messaging;

/**
 * Mirrors the native Postgres {@code outbox_status} enum; constant names match the DB labels exactly
 * so Hibernate's {@code @JdbcTypeCode(NAMED_ENUM)} binds by {@link Enum#name()}. {@code PENDING} is
 * the only status the relay drains; {@code PUBLISHED} and {@code FAILED} are terminal.
 */
enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
