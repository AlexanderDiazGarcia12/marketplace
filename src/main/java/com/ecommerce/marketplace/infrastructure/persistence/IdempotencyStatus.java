package com.ecommerce.marketplace.infrastructure.persistence;

/**
 * Mirrors the native Postgres {@code idempotency_status} enum; constant names match the DB labels so
 * Hibernate binds by {@link Enum#name()}. Kept distinct from the application-layer
 * {@link com.ecommerce.marketplace.application.ports.out.IdempotencyRecord.IdempotencyStatus} so the
 * port never depends on this persistence detail.
 *
 * <ul>
 *   <li>{@code IN_PROGRESS} — the original request is still being processed; a retry is told the
 *       purchase is already in flight (409).</li>
 *   <li>{@code COMPLETED} — terminal success; a retry is answered from the stored snapshot without
 *       re-executing business logic.</li>
 * </ul>
 */
enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
