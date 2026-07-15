package com.ecommerce.marketplace.application.ports.out;

import io.vavr.collection.Seq;

/**
 * Read projection of one rejected CSV data row for the status view (US-18): its 1-based
 * {@code rowNumber}, the {@code rawLine} that failed, and the accumulated human-readable
 * {@code reasons} deserialized from the {@code import_job_errors.error_reason} JSONB array.
 *
 * <p>The reasons come back as a {@link Seq} of plain strings — the same shape the US-17 worker
 * wrote through {@link ImportErrorRepositoryPort#recordRowError} — so the view renders them as a
 * list, never as raw JSON.</p>
 */
public record ImportRowError(int rowNumber, String rawLine, Seq<String> reasons) {
}
