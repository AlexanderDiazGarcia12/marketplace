package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;

import java.time.OffsetDateTime;

/**
 * Full read projection of a single import job for the status view (US-18): its lifecycle
 * {@link ImportJobState}, the three row counters written once at the terminal transition
 * ({@link ImportJobCounters}), the {@code original_filename} shown to the operator, and the
 * {@code created_at}/{@code completed_at} timestamps. {@code completedAt} is {@code null} while the
 * job is still {@code PENDING}/{@code PROCESSING} — the DB column is only stamped on a terminal
 * transition.
 *
 * <p>This is the read counterpart to {@link NewImportJob} (the write shape): it carries the columns
 * the UI needs to render progress without exposing the persistence-layer entity.</p>
 */
public record ImportJobDetail(
        ImportJobId id,
        ImportJobState state,
        ImportJobCounters counters,
        String originalFilename,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {
}
