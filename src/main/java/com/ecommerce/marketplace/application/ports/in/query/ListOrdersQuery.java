package com.ecommerce.marketplace.application.ports.in.query;

import com.ecommerce.marketplace.application.ports.query.PageRequest;
import com.ecommerce.marketplace.domain.model.order.OrderStatus;
import io.vavr.control.Option;

/**
 * Input query for {@link com.ecommerce.marketplace.application.ports.in.ListOrdersUseCase}.
 *
 * <p>{@code status} is an optional {@link OrderStatus} filter, modeled with {@link Option} rather
 * than a nullable field so the use case never null-checks — the empty case means "all statuses".
 * {@code pageRequest} bounds the result set, mirroring {@code SearchProductsCommand}.</p>
 */
public record ListOrdersQuery(Option<OrderStatus> status, PageRequest pageRequest) {

    public ListOrdersQuery {
        if (status == null || pageRequest == null) {
            throw new IllegalArgumentException("ListOrdersQuery requires a non-null (possibly empty) status filter and a page request");
        }
    }
}
