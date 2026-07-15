package com.ecommerce.marketplace.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface-based projection over the {@code import_jobs} columns the status view needs.
 * Native rather than entity-backed so the {@link ImportJobEntity} write mapping stays untouched;
 * {@link ImportJobMapper} turns it into the application-layer {@code ImportJobDetail} so it never
 * crosses the hexagon boundary.
 */
interface ImportJobDetailRow {

    UUID getId();

    String getStatus();

    long getTotalRows();

    long getAcceptedRows();

    long getRejectedRows();

    String getOriginalFilename();

    Instant getCreatedAt();

    Instant getCompletedAt();
}
