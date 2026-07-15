package com.ecommerce.marketplace.application.ports.out;

import io.vavr.collection.Seq;

/**
 * Read projection of one rejected CSV data row for the status view: its 1-based {@code rowNumber},
 * the {@code rawLine} that failed, and the human-readable {@code reasons} as a {@link Seq} of plain
 * strings so the view renders them as a list, never raw JSON.
 */
public record ImportRowError(int rowNumber, String rawLine, Seq<String> reasons) {
}
