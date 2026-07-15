package com.ecommerce.marketplace.application.ports.out;

/**
 * Final row tallies written once, on the terminal transition to {@code COMPLETED}. The worker
 * derives them idempotently at end-of-file ({@code rejected} = current error-row count,
 * {@code total} = rows read this pass, {@code accepted} = total − rejected) so a full redelivery
 * converges instead of double-counting. All three are non-negative.
 */
public record ImportJobCounters(long total, long accepted, long rejected) {

    public ImportJobCounters {
        if (total < 0 || accepted < 0 || rejected < 0) {
            throw new IllegalArgumentException("ImportJobCounters requires non-negative tallies");
        }
    }
}
