package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.event.OrderPlaced;
import com.ecommerce.marketplace.application.ports.in.PurchaseProductUseCase;
import com.ecommerce.marketplace.application.ports.in.command.PurchaseCommand;
import com.ecommerce.marketplace.application.ports.out.EventPublisherPort;
import com.ecommerce.marketplace.application.ports.out.IdempotencyRecord;
import com.ecommerce.marketplace.application.ports.out.IdempotencyRecord.IdempotencyStatus;
import com.ecommerce.marketplace.application.ports.out.IdempotencyStorePort;
import com.ecommerce.marketplace.application.ports.out.OrderRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.PaymentGatewayPort;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.PurchaseSnapshot;
import com.ecommerce.marketplace.application.ports.out.PurchaseSnapshotCodec;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.Order;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.order.OrderItem;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Try;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Plain-Java implementation of {@link PurchaseProductUseCase} (US-22), wired via an explicit
 * {@code @Bean} in {@code infrastructure.config.SpringDependencyInjectionConfig} — no Spring
 * stereotype annotations here, keeping the application layer framework-free (enforced by
 * {@code HexagonalArchitectureTest}).
 *
 * <p><strong>The transaction boundary is NOT here.</strong> {@code @Transactional}/
 * {@code TransactionTemplate} are Spring types forbidden in this layer, so this service opens no
 * transaction of its own — exactly like {@code ImportProductsService}. {@code CheckoutController}
 * wraps {@link #purchase(PurchaseCommand)} in the checkout Unit of Work (and, on a declined
 * payment, {@link #recordRejection(PurchaseCommand)} in a second, independent
 * {@code REQUIRES_NEW} transaction). The CA's phrase "@Transactional demarcado en application.service"
 * means "the whole purchase flow is one atomic Unit of Work", with the mechanism living in
 * infrastructure, mirroring {@code ImportProductsController}.</p>
 *
 * <p><strong>{@link #purchase} — the main-transaction flow, one {@code flatMap} chain, no
 * try/catch of business flow.</strong> begin idempotency &rarr; decrease stock (versioned UPDATE)
 * &rarr; charge &rarr; build a {@code CONFIRMED} {@link Order} &rarr; save it &rarr; publish
 * {@code OrderPlaced} to the outbox &rarr; complete the idempotency key with a snapshot of the
 * order. Stock is decremented <em>before</em> the charge on purpose: if the charge is declined the
 * caller rolls this whole transaction back and the decrement is undone automatically, with no
 * compensating stock logic. On a declined payment this method returns
 * {@code Either.left(PaymentRejected)} immediately and deliberately does <em>not</em> persist a
 * {@code REJECTED} order or complete the key — those writes must survive this transaction's
 * rollback, so they are the caller's second transaction's job.</p>
 *
 * <p><strong>{@link #recordRejection} — the compensating write.</strong> Not on the
 * {@link PurchaseProductUseCase} port: it is an infrastructure-orchestration detail (a second
 * physical transaction), not part of the stable public contract, so only the concrete type exposes
 * it. It re-reads the product for the price snapshot (the main transaction's decrement was rolled
 * back, so the aggregate is gone from the persistence context), builds a {@code REJECTED} order,
 * saves it and completes the key with a snapshot — so a retry with the same key is answered from
 * the recorded rejection instead of re-charging. Still 100% plain Java / Vavr.</p>
 *
 * <p><strong>Replay.</strong> When {@code begin} reports the key already {@code COMPLETED} (a
 * retry), {@link #replay} short-circuits: it re-reads the persisted order (the snapshot carries
 * only its id) and answers from the order's own {@code status} — {@code Right(order)} for a
 * confirmed purchase, {@code Left(PaymentRejected)} for a recorded rejection — without touching
 * stock, payment or the outbox again.</p>
 */
public final class PurchaseProductService implements PurchaseProductUseCase {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String REPLAYED_REJECTION_REASON =
            "This purchase was already attempted and declined; the payment was not retried";

    private final IdempotencyStorePort idempotencyStore;
    private final ProductRepositoryPort productRepository;
    private final PaymentGatewayPort paymentGateway;
    private final OrderRepositoryPort orderRepository;
    private final EventPublisherPort eventPublisher;
    private final PurchaseSnapshotCodec snapshotCodec;

    public PurchaseProductService(
            IdempotencyStorePort idempotencyStore,
            ProductRepositoryPort productRepository,
            PaymentGatewayPort paymentGateway,
            OrderRepositoryPort orderRepository,
            EventPublisherPort eventPublisher,
            PurchaseSnapshotCodec snapshotCodec) {
        this.idempotencyStore = idempotencyStore;
        this.productRepository = productRepository;
        this.paymentGateway = paymentGateway;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.snapshotCodec = snapshotCodec;
    }

    @Override
    public Either<Failure, Order> purchase(PurchaseCommand command) {
        return idempotencyStore.begin(command.idempotencyKey(), requestHash(command))
                .flatMap(record -> record.status() == IdempotencyStatus.COMPLETED
                        ? replay(record)
                        : executeFreshPurchase(command));
    }

    public Either<Failure, Order> recordRejection(PurchaseCommand command) {
        return orderRepository.findByIdempotencyKey(command.idempotencyKey())
                .map(Either::<Failure, Order>right)
                .getOrElse(() -> recordFreshRejection(command));
    }

    private Either<Failure, Order> recordFreshRejection(PurchaseCommand command) {
        return productRepository.findBySku(command.sku())
                .toEither(() -> (Failure) new Failure.ProductNotFound(command.sku()))
                .flatMap(product -> buildOrder(command, product.price(), OrderId.generate(), Order::rejected))
                .flatMap(this::persistRejected);
    }

    private Either<Failure, Order> executeFreshPurchase(PurchaseCommand command) {
        return productRepository.decreaseStock(command.sku(), command.quantity())
                .flatMap(decremented -> chargeAndConfirm(command, decremented.price()));
    }

    private Either<Failure, Order> chargeAndConfirm(PurchaseCommand command, Money unitPrice) {
        return paymentGateway.charge(command.paymentToken(), unitPrice.multiply(command.quantity()))
                .flatMap(confirmation -> buildOrder(command, unitPrice, OrderId.generate(), Order::confirmed))
                .flatMap(this::persistConfirmed);
    }

    private Either<Failure, Order> persistConfirmed(Order order) {
        return orderRepository.save(order)
                .flatMap(saved -> eventPublisher.publish(orderPlaced(saved)).map(published -> saved))
                .flatMap(this::completeWithSnapshot);
    }

    private Either<Failure, Order> persistRejected(Order order) {
        return orderRepository.save(order)
                .flatMap(this::completeWithSnapshot);
    }

    private Either<Failure, Order> completeWithSnapshot(Order order) {
        String snapshot = snapshotCodec.serialize(new PurchaseSnapshot(order.id().value().toString()));
        return idempotencyStore.complete(order.idempotencyKey(), snapshot).map(record -> order);
    }

    private Either<Failure, Order> replay(IdempotencyRecord record) {
        return snapshotCodec.deserialize(record.responseSnapshot())
                .flatMap(snapshot -> OrderId.of(snapshot.orderId()).toOption())
                .flatMap(orderRepository::findById)
                .toEither(() -> (Failure) new Failure.DuplicateOrderRequest(record.key()))
                .flatMap(this::replayOutcome);
    }

    private Either<Failure, Order> replayOutcome(Order order) {
        return switch (order.status()) {
            case CONFIRMED -> Either.right(order);
            case REJECTED -> Either.left(new Failure.PaymentRejected(REPLAYED_REJECTION_REASON));
        };
    }

    private Either<Failure, Order> buildOrder(PurchaseCommand command, Money unitPrice, OrderId orderId, OrderFactory factory) {
        return OrderItem.of(command.sku(), command.quantity(), unitPrice)
                .flatMap(item -> factory.build(orderId, List.of(item), command.paymentToken(), command.idempotencyKey()));
    }

    private static OrderPlaced orderPlaced(Order order) {
        OrderItem line = order.items().head();
        return new OrderPlaced(
                order.id().value().toString(),
                line.sku().value(),
                line.quantity(),
                order.totalAmount().amount().toPlainString());
    }

    private static String requestHash(PurchaseCommand command) {
        String payload = command.sku().value() + '|' + command.quantity() + '|' + command.paymentToken().value();
        byte[] digest = Try.of(() -> MessageDigest.getInstance(HASH_ALGORITHM))
                .map(algorithm -> algorithm.digest(payload.getBytes(StandardCharsets.UTF_8)))
                .get();
        return HexFormat.of().formatHex(digest);
    }

    @FunctionalInterface
    private interface OrderFactory {
        Either<Failure, Order> build(OrderId id, Seq<OrderItem> items, PaymentToken paymentToken, IdempotencyKey idempotencyKey);
    }
}
