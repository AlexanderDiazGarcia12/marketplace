package com.ecommerce.marketplace.infrastructure.persistence;

/**
 * Mirrors the native Postgres {@code import_job_status} enum; constant names match the DB labels so
 * Hibernate binds by {@link Enum#name()}.
 *
 * <ul>
 *   <li>{@code PENDING} — job accepted, awaiting the async worker; the only status written on
 *       creation.</li>
 *   <li>{@code PROCESSING} — the worker is ingesting rows.</li>
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
