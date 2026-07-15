package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.command.ImportJobId;

import java.time.OffsetDateTime;

/**
 * Full read projection of a single import job for the status view: its lifecycle
 * {@link ImportJobState}, the {@link ImportJobCounters}, the original filename and the created/
 * completed timestamps. {@code completedAt} is {@code null} until a terminal transition. This is
 * the read counterpart to {@link NewImportJob}, carrying what the UI needs without exposing the
 * persistence-layer entity.
 */
public record ImportJobDetail(
        ImportJobId id,
        ImportJobState state,
        ImportJobCounters counters,
        String originalFilename,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {
}
