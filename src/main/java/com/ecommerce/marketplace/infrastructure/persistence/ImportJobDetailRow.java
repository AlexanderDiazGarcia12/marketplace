package com.ecommerce.marketplace.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface-based projection over the {@code import_jobs} columns the US-18 status view
 * needs. Native (not entity-backed) because the {@link ImportJobEntity} mapping deliberately covers
 * only the write columns US-16 inserts — reading the counters, {@code status} label and
 * {@code completed_at} through a projection keeps that entity untouched. Confined to
 * {@code infrastructure.persistence}: {@link ImportJobDetailRowMapper} turns it into the
 * application-layer {@code ImportJobDetail} so it never crosses the hexagon boundary.
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
