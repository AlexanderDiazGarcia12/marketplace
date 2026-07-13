package com.ecommerce.marketplace.application.ports.query;

/**
 * Framework-free pagination request for application-layer queries.
 *
 * <p>Deliberately independent of {@code org.springframework.data.domain.Pageable} so that
 * {@code application.ports} carries zero Spring dependency (US-04 CA). {@code page} is
 * zero-based; {@code size} is bounded by {@link #MAX_SIZE} to protect the database from
 * unbounded scans.</p>
 *
 * <p>Lives in {@code application.ports.query} — a side-neutral package shared by both the input
 * port ({@code SearchProductUseCase}) and the output port ({@code ProductRepositoryPort}). It was
 * moved here from {@code application.ports.in.query} in US-13: an out-port must not depend on a
 * package owned by the in-side, a coupling the US-04/US-09/US-11 audits flagged as deferred to
 * this story.</p>
 */
public record PageRequest(int page, int size) {

    private static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    public long offset() {
        return (long) page * size;
    }
}
