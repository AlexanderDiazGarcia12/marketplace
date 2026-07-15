package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.ListOrdersUseCase;
import com.ecommerce.marketplace.application.ports.in.query.ListOrdersQuery;
import com.ecommerce.marketplace.application.ports.out.OrderRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.OrderSummary;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Implementation of {@link ListOrdersUseCase}. Forwards the bounded query to the repository
 * projection and relays its result.
 */
public final class ListOrdersService implements ListOrdersUseCase {

    private final OrderRepositoryPort orderRepository;

    public ListOrdersService(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Either<Failure, Page<OrderSummary>> list(ListOrdersQuery query) {
        return orderRepository.list(query.status(), query.pageRequest());
    }
}
