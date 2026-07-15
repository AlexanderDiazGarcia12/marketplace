package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.query.ListOrdersQuery;
import com.ecommerce.marketplace.application.ports.out.OrderSummary;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Paginated admin listing of all orders, newest first, optionally filtered by status. An empty
 * result is a valid {@link Page}, not a {@link Failure}.
 */
public interface ListOrdersUseCase {

    Either<Failure, Page<OrderSummary>> list(ListOrdersQuery query);
}
