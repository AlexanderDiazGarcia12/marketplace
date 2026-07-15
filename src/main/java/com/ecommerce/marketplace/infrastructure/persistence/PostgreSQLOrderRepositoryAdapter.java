package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.out.OrderRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.OrderSummary;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.application.ports.query.PageRequest;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.Order;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.order.OrderStatus;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Data JPA adapter for {@link OrderRepositoryPort} — the sole place where
 * {@code orders}/{@code order_items} rows are read or written; maps every persistence type back
 * to the pure {@link Order} aggregate via {@link OrderMapper} so no {@code @Entity} escapes this
 * package.
 *
 * <p>{@link #save(Order)} joins the caller's ambient transaction (propagation {@code REQUIRED}),
 * so the order write is atomic with whatever else that transaction is doing. The
 * {@code uq_orders_idempotency_key} constraint is the DB-level backstop against a duplicate order
 * for the same idempotency key.</p>
 */
public final class PostgreSQLOrderRepositoryAdapter implements OrderRepositoryPort {

    private final EntityManager entityManager;
    private final SpringDataOrderJpaRepository jpaRepository;
    private final TransactionTemplate transactionTemplate;

    public PostgreSQLOrderRepositoryAdapter(
            EntityManager entityManager,
            SpringDataOrderJpaRepository jpaRepository,
            TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.jpaRepository = jpaRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Either<Failure, Order> save(Order order) {
        return transactionTemplate.execute(status -> persist(order));
    }

    private Either<Failure, Order> persist(Order order) {
        JPAOrderEntity entity = OrderMapper.toEntity(order);
        entityManager.persist(entity);
        entityManager.flush();
        return Either.right(OrderMapper.toDomain(entity));
    }

    @Override
    public Option<Order> findById(OrderId id) {
        return Option.ofOptional(jpaRepository.findByIdWithItems(id.value()))
                .map(OrderMapper::toDomain);
    }

    @Override
    public Option<Order> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return Option.ofOptional(jpaRepository.findByIdempotencyKeyWithItems(idempotencyKey.value()))
                .map(OrderMapper::toDomain);
    }

    @Override
    public Either<Failure, Page<OrderSummary>> list(Option<OrderStatus> status, PageRequest pageRequest) {
        Seq<OrderSummary> content = List.ofAll(
                        status.fold(
                                () -> jpaRepository.findSummaries(pageRequest.size(), pageRequest.offset()),
                                filter -> jpaRepository.findSummariesByStatus(filter.name(), pageRequest.size(), pageRequest.offset())))
                .map(OrderMapper::toSummary);
        long total = status.fold(jpaRepository::countAll, filter -> jpaRepository.countByStatus(filter.name()));
        return Either.right(Page.of(content, pageRequest, total));
    }
}
