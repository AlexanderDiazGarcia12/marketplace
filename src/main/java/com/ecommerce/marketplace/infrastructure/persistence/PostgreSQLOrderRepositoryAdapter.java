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
 * Spring Data JPA adapter for {@link OrderRepositoryPort} (US-22). The sole place where
 * {@code orders}/{@code order_items} rows are read/written; maps every persistence type back to the
 * pure {@link Order} aggregate via {@link OrderMapper} so no {@code @Entity} escapes this package.
 *
 * <p><strong>{@link #save(Order)} — same Unit of Work as the rest of checkout.</strong> The insert
 * (order + cascaded line items) goes through {@link EntityManager#persist} inside
 * {@code transactionTemplate.execute} on the <em>shared</em> {@code marketplaceTransactionTemplate}
 * bean, whose propagation is the default {@code REQUIRED}: it therefore <em>joins</em> whatever
 * transaction the checkout controller already opened rather than starting its own physical one, so
 * the order write is atomic with the stock decrement, the outbox insert and the idempotency
 * completion — exactly like {@code PostgreSQLProductRepositoryAdapter}'s mutating methods. A
 * client-assigned UUID id means {@code persist} always INSERTs (no {@code merge}/SELECT dance), and
 * the {@code uq_orders_idempotency_key} constraint is the DB-level backstop guaranteeing one order
 * per idempotency key even if the application guard ever had a bug.</p>
 *
 * <p><strong>{@link #findById(OrderId)}</strong> reads the order with its items in one fetch join
 * (see {@link SpringDataOrderJpaRepository#findByIdWithItems}) so the aggregate is fully rebuilt
 * without lazy loading — used by the checkout replay path to answer a retried purchase from the
 * persisted row.</p>
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
