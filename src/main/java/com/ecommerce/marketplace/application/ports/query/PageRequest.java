package com.ecommerce.marketplace.application.ports.query;

/**
 * Framework-free pagination request for application-layer queries, independent of
 * {@code org.springframework.data.domain.Pageable} so {@code application.ports} carries zero Spring
 * dependency. {@code page} is zero-based; {@code size} is bounded by {@link #MAX_SIZE} to protect
 * the database from unbounded scans. Lives in {@code application.ports.query}, a side-neutral
 * package shared by both the input and output ports.
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
