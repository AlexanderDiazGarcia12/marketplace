package com.ecommerce.marketplace.infrastructure.persistence;

/**
 * Mirrors the native Postgres {@code import_job_status} enum (V1). Constant names match the DB
 * labels exactly so Hibernate's {@code @JdbcTypeCode(NAMED_ENUM)} binds by {@link Enum#name()},
 * matching the {@code OutboxStatus}/{@code product_category} convention.
 *
 * <ul>
 *   <li>{@code PENDING} — job accepted, awaiting the async worker; the only status US-16 writes.</li>
 *   <li>{@code PROCESSING} — the US-17 worker is ingesting rows.</li>
 *   <li>{@code COMPLETED} — terminal success (all rows processed).</li>
 *   <li>{@code FAILED} — terminal failure.</li>
 * </ul>
 */
enum ImportJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
