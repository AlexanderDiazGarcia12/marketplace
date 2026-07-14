package com.ecommerce.marketplace.application.ports.out;

/**
 * Final row tallies written once, on the terminal transition to {@code COMPLETED} (US-17). The
 * worker never increments these row-by-row: on reaching the end of the file it derives them
 * idempotently ({@code rejected} = current error-row count for the job, {@code total} = rows read
 * this pass, {@code accepted} = total − rejected) so a full redelivery converges to the same
 * numbers instead of double-counting. All three are non-negative, matching the {@code import_jobs}
 * CHECK constraints.
 */
public record ImportJobCounters(long total, long accepted, long rejected) {

    public ImportJobCounters {
        if (total < 0 || accepted < 0 || rejected < 0) {
            throw new IllegalArgumentException("ImportJobCounters requires non-negative tallies");
        }
    }
}
