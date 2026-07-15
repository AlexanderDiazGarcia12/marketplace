package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.ports.in.ListOrdersUseCase;
import com.ecommerce.marketplace.application.ports.in.query.ListOrdersQuery;
import com.ecommerce.marketplace.application.ports.out.OrderRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.OrderSummary;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;

/**
 * Plain-Java implementation of {@link ListOrdersUseCase}, wired via an explicit {@code @Bean} in
 * {@code infrastructure.config.SpringDependencyInjectionConfig} — no Spring stereotype annotations
 * here, keeping the application layer framework-free.
 *
 * <p>Mirrors {@code SearchProductService}: the query's construction already guarantees a bounded
 * {@code PageRequest} and an {@link io.vavr.control.Option} status filter, so the use case simply
 * forwards them to the repository projection and relays its {@code Either}. An empty page is a valid
 * result, not a {@link Failure}.</p>
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
