package com.ecommerce.marketplace.application.ports.query;

import io.vavr.collection.Seq;

/**
 * Framework-free paginated result, application-layer counterpart of {@link PageRequest}.
 *
 * <p>Deliberately {@code application}-owned rather than {@code org.springframework.data.domain.Page}
 * (per US-13's CA) so that {@code application.ports} never leaks a Spring type into contracts
 * consumed by both web and persistence adapters.</p>
 *
 * <p>Lives in {@code application.ports.query} — a side-neutral package shared by both the input
 * port ({@code SearchProductUseCase}) and the output port ({@code ProductRepositoryPort}). It was
 * moved here from {@code application.ports.in.query} in US-13 to remove the inverted coupling of an
 * out-port depending on an in-side package.</p>
 *
 * @param content     the items for the requested page, in result order
 * @param page        zero-based page index this result corresponds to
 * @param size        the page size that was requested
 * @param totalElements total number of elements across all pages
 */
public record Page<T>(Seq<T> content, int page, int size, long totalElements) {

    public Page {
        if (content == null || page < 0 || size < 1 || totalElements < 0) {
            throw new IllegalArgumentException("Page requires non-null content, a non-negative page index, a positive size and a non-negative total");
        }
    }

    public static <T> Page<T> of(Seq<T> content, PageRequest request, long totalElements) {
        return new Page<>(content, request.page(), request.size(), totalElements);
    }

    public int totalPages() {
        return (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }
}
