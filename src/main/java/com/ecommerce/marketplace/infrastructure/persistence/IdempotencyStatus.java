package com.ecommerce.marketplace.infrastructure.persistence;

/**
 * Mirrors the native Postgres {@code idempotency_status} enum (V1). Constant names match the DB
 * labels exactly so Hibernate's {@code @JdbcTypeCode(NAMED_ENUM)} binds by {@link Enum#name()},
 * matching the {@code ImportJobStatus}/{@code OutboxStatus} convention.
 *
 * <p>Kept distinct from the application-layer
 * {@link com.ecommerce.marketplace.application.ports.out.IdempotencyRecord.IdempotencyStatus}
 * (same labels, different concern): this one is a persistence detail confined to
 * {@code infrastructure.persistence}; {@link IdempotencyKeyMapper} translates between the two so the
 * application port never depends on a Hibernate-bound type.</p>
 *
 * <ul>
 *   <li>{@code IN_PROGRESS} — the original request is still being processed; a retry must be told the
 *       purchase is already in flight (409).</li>
 *   <li>{@code COMPLETED} — terminal success; a retry is answered from the stored snapshot without
 *       re-executing business logic.</li>
 * </ul>
 */
enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
