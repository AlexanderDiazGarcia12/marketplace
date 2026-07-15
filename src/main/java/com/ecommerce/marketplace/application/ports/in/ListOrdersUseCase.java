package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.query.ListOrdersQuery;
import com.ecommerce.marketplace.application.ports.out.OrderSummary;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Input port: paginated admin listing of all orders, ordered by creation date descending and
 * optionally filtered by status.
 *
 * <p>Returns {@code Either<Failure, Page<OrderSummary>>} with the application-owned {@link Page}
 * (never {@code org.springframework.data.domain.Page}), mirroring {@link SearchProductUseCase}. An
 * empty result set is a valid {@link Page} with zero elements, not a {@link Failure}.</p>
 */
public interface ListOrdersUseCase {

    Either<Failure, Page<OrderSummary>> list(ListOrdersQuery query);
}
