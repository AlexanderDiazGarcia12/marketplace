package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.in.command.PurchaseCommand;
import com.ecommerce.marketplace.application.service.PurchaseProductService;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.Order;
import com.ecommerce.marketplace.domain.model.order.OrderStatus;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import com.ecommerce.marketplace.infrastructure.messaging.KafkaEventPublisherAdapter;
import com.ecommerce.marketplace.infrastructure.payment.FakePaymentGatewayAdapter;
import io.vavr.control.Either;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for the transactional checkout Unit of Work against the real Docker Compose
 * Postgres. It wires the real adapters and {@link PurchaseProductService} with no mocked ports;
 * the one Mockito stub ({@link SpringDataProductJpaRepository#findBySku}) only injects a competing
 * version bump at a precise instant to make the optimistic-lock retry deterministic.
 *
 * <p>{@link #checkout(PurchaseCommand)} replicates the controller's two-phase split: a main
 * {@code REQUIRED} transaction wraps {@link PurchaseProductService#purchase} (rollback-only on any
 * {@code Left}), and on {@link Failure.PaymentRejected} a separate {@code REQUIRES_NEW} transaction
 * runs {@link PurchaseProductService#recordRejection}, exercising rollback restoring stock and the
 * compensating write surviving it.</p>
 *
 * <p>{@code @Transactional(NOT_SUPPORTED)} disables the shared per-method rollback so every
 * {@code checkout} commits for real, matching independent HTTP requests; {@link #cleanUp()} then
 * deletes this test's rows by their {@code IT22-} prefix.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.docker.compose.skip.in-tests=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PurchaseProductServiceIT {

    private static final String APPROVED = "approved-it-card";
    private static final String DECLINED = "insufficient-funds-it-card";

    @Autowired
    private SpringDataProductJpaRepository productJpaRepository;

    @Autowired
    private SpringDataOrderJpaRepository orderJpaRepository;

    @Autowired
    private SpringDataIdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate requiredTransaction;
    private TransactionTemplate requiresNewTransaction;
    private PurchaseProductService service;

    @BeforeEach
    void wireAdapters() {
        requiredTransaction = new TransactionTemplate(transactionManager);
        requiresNewTransaction = new TransactionTemplate(transactionManager);
        requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        ObjectMapper objectMapper = new ObjectMapper();
        service = new PurchaseProductService(
                new PostgreSQLIdempotencyStoreAdapter(idempotencyKeyJpaRepository, transactionManager),
                new PostgreSQLProductRepositoryAdapter(productJpaRepository, entityManager, requiredTransaction),
                new FakePaymentGatewayAdapter(),
                new PostgreSQLOrderRepositoryAdapter(entityManager, orderJpaRepository, requiredTransaction),
                new KafkaEventPublisherAdapter(entityManager, objectMapper),
                new JacksonPurchaseSnapshotCodec(objectMapper));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE idempotency_key LIKE 'IT22-%')");
        jdbcTemplate.update("DELETE FROM outbox_events WHERE aggregate_id IN (SELECT id::text FROM orders WHERE idempotency_key LIKE 'IT22-%')");
        jdbcTemplate.update("DELETE FROM orders WHERE idempotency_key LIKE 'IT22-%'");
        jdbcTemplate.update("DELETE FROM idempotency_keys WHERE key LIKE 'IT22-%'");
        jdbcTemplate.update("DELETE FROM products WHERE sku LIKE 'IT22-%'");
    }

    @Test
    void freshSuccessfulPurchaseDecrementsStockPersistsConfirmedOrderOutboxRowAndCompletesKey() {
        SKU sku = insertProduct("IT22-ok", 5, "29.99");
        String key = "IT22-key-ok";

        Either<Failure, Order> result = checkout(command("IT22-ok", 2, APPROVED, key));

        assertThat(result.isRight()).isTrue();
        Order order = result.get();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);

        assertThat(stockOf(sku)).isEqualTo(3);
        assertThat(orderCount(key)).isEqualTo(1L);
        assertThat(orderStatus(key)).isEqualTo("CONFIRMED");
        assertThat(totalAmount(key)).isEqualByComparingTo("59.98");

        Map<String, Object> item = orderItem(key);
        assertThat(((Number) item.get("quantity")).intValue()).isEqualTo(2);
        assertThat(new BigDecimal(item.get("unit_price").toString())).isEqualByComparingTo("29.99");

        assertThat(outboxCount(order.id().value().toString())).isEqualTo(1L);
        assertThat(idempotencyStatus(key)).isEqualTo("COMPLETED");
    }

    @Test
    void replayWithSameKeyAndBodyReturnsSameOrderWithoutSecondDecrementOrDuplicates() {
        SKU sku = insertProduct("IT22-replay", 5, "29.99");
        String key = "IT22-key-replay";
        PurchaseCommand command = command("IT22-replay", 2, APPROVED, key);
        Order first = checkout(command).get();

        Either<Failure, Order> replay = checkout(command);

        assertThat(replay.isRight()).isTrue();
        assertThat(replay.get().id()).isEqualTo(first.id());
        assertThat(stockOf(sku)).isEqualTo(3);
        assertThat(orderCount(key)).isEqualTo(1L);
        assertThat(outboxCount(first.id().value().toString())).isEqualTo(1L);
    }

    @Test
    void declinedPaymentRollsBackStockRecordsRejectedOrderCompletesKeyAndRetryReplaysWithoutRecharge() {
        SKU sku = insertProduct("IT22-declined", 4, "29.99");
        String key = "IT22-key-declined";
        PurchaseCommand command = command("IT22-declined", 2, DECLINED, key);

        Either<Failure, Order> declined = checkout(command);

        assertThat(declined.isLeft()).isTrue();
        assertThat(declined.getLeft()).isInstanceOf(Failure.PaymentRejected.class);
        assertThat(stockOf(sku)).isEqualTo(4);
        assertThat(orderCount(key)).isEqualTo(1L);
        assertThat(orderStatus(key)).isEqualTo("REJECTED");
        assertThat(idempotencyStatus(key)).isEqualTo("COMPLETED");
        assertThat(outboxCountForKey(key)).isEqualTo(0L);

        Either<Failure, Order> retry = checkout(command);

        assertThat(retry.isLeft()).isTrue();
        assertThat(retry.getLeft()).isInstanceOf(Failure.PaymentRejected.class);
        assertThat(stockOf(sku)).isEqualTo(4);
        assertThat(orderCount(key)).isEqualTo(1L);
    }

    @Test
    void insufficientStockCreatesNoOrderLeavesKeyInProgressAndStockUnchanged() {
        SKU sku = insertProduct("IT22-short", 1, "29.99");
        String key = "IT22-key-short";

        Either<Failure, Order> result = checkout(command("IT22-short", 5, APPROVED, key));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(Failure.InsufficientStock.class);
        assertThat(orderCount(key)).isEqualTo(0L);
        assertThat(stockOf(sku)).isEqualTo(1);
        assertThat(idempotencyStatus(key)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void concurrentPurchasesAgainstLowStockNeverOversellUnderOptimisticRetry() throws Exception {
        int initialStock = 2;
        SKU sku = insertProduct("IT22-race", initialStock, "10.00");
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLine = new CountDownLatch(1);

        List<Future<Either<Failure, Order>>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String key = "IT22-race-" + i;
            futures.add(pool.submit(() -> {
                startLine.await();
                return checkout(command("IT22-race", 1, APPROVED, key));
            }));
        }
        startLine.countDown();

        int confirmed = 0;
        for (Future<Either<Failure, Order>> future : futures) {
            if (future.get(20, TimeUnit.SECONDS).isRight()) {
                confirmed++;
            }
        }
        pool.shutdown();

        assertThat(confirmed).isBetween(1, initialStock);
        assertThat(stockOf(sku)).isEqualTo(initialStock - confirmed);
        assertThat(confirmedOrderCount("IT22-race-%")).isEqualTo((long) confirmed);
    }

    @Test
    void decreaseStockRetriesAfterAConcurrentVersionBumpThenSucceeds() {
        SKU sku = insertProduct("IT22-retry", 5, "10.00");
        AtomicInteger reads = new AtomicInteger();
        PostgreSQLProductRepositoryAdapter adapter = adapterWhoseReadBumpsVersion("IT22-retry", reads, 1);

        Either<Failure, Product> result = requiredTransaction.execute(status -> adapter.decreaseStock(sku, 1));

        assertThat(result.isRight()).isTrue();
        assertThat(reads.get()).isGreaterThanOrEqualTo(2);
        assertThat(stockOf(sku)).isEqualTo(4);
    }

    @Test
    void decreaseStockExhaustsToConcurrentStockConflictAfterThreeFailedAttempts() {
        SKU sku = insertProduct("IT22-exhaust", 5, "10.00");
        AtomicInteger reads = new AtomicInteger();
        PostgreSQLProductRepositoryAdapter adapter = adapterWhoseReadBumpsVersion("IT22-exhaust", reads, Integer.MAX_VALUE);

        Either<Failure, Product> result = requiredTransaction.execute(status -> adapter.decreaseStock(sku, 1));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo(new Failure.ConcurrentStockConflict(sku));
        assertThat(reads.get()).isEqualTo(3);
        assertThat(stockOf(sku)).isEqualTo(5);
    }

    /**
     * Wires the real product adapter over a repository whose {@code findBySku} delegates to the real
     * repository but commits a competing {@code version} bump in a separate transaction on its first
     * {@code bumpsFor} reads, reproducing what a concurrent buyer does between the adapter's read and
     * its versioned UPDATE. Only the read is instrumented; the UPDATE hits real Postgres.
     */
    private PostgreSQLProductRepositoryAdapter adapterWhoseReadBumpsVersion(String sku, AtomicInteger reads, int bumpsFor) {
        SpringDataProductJpaRepository instrumented = mock(SpringDataProductJpaRepository.class);
        when(instrumented.findBySku(sku)).thenAnswer(invocation -> {
            Optional<JPAProductEntity> row = productJpaRepository.findBySku(sku);
            if (reads.getAndIncrement() < bumpsFor) {
                bumpVersionInSeparateTransaction(sku);
            }
            return row;
        });
        when(instrumented.decreaseStockIfVersionMatches(anyString(), anyInt(), anyInt())).thenAnswer(invocation ->
                productJpaRepository.decreaseStockIfVersionMatches(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        return new PostgreSQLProductRepositoryAdapter(instrumented, entityManager, requiredTransaction);
    }

    private Either<Failure, Order> checkout(PurchaseCommand command) {
        Either<Failure, Order> result = requiredTransaction.execute(status -> {
            Either<Failure, Order> outcome = service.purchase(command);
            outcome.peekLeft(failure -> status.setRollbackOnly());
            return outcome;
        });
        if (result.isLeft() && result.getLeft() instanceof Failure.PaymentRejected) {
            requiresNewTransaction.execute(status -> {
                Either<Failure, Order> outcome = service.recordRejection(command);
                outcome.peekLeft(ignored -> status.setRollbackOnly());
                return outcome;
            });
        }
        return result;
    }

    private void bumpVersionInSeparateTransaction(String sku) {
        requiresNewTransaction.execute(status ->
                jdbcTemplate.update("UPDATE products SET version = version + 1 WHERE sku = ?", sku));
    }

    private SKU insertProduct(String sku, int stock, String price) {
        jdbcTemplate.update(
                "INSERT INTO products (sku, name, description, category, price, stock, weight_kg) "
                        + "VALUES (?, ?, ?, CAST(? AS product_category), ?, ?, ?)",
                sku, "IT22 product", "integration test product", "ELECTRONICS", new BigDecimal(price), stock, new BigDecimal("1.000"));
        return new SKU(sku);
    }

    private static PurchaseCommand command(String sku, int quantity, String paymentToken, String key) {
        return new PurchaseCommand(new SKU(sku), quantity, new PaymentToken(paymentToken), new IdempotencyKey(key));
    }

    private int stockOf(SKU sku) {
        return jdbcTemplate.queryForObject("SELECT stock FROM products WHERE sku = ?", Integer.class, sku.value());
    }

    private long orderCount(String key) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE idempotency_key = ?", Long.class, key);
    }

    private long confirmedOrderCount(String keyPattern) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE idempotency_key LIKE ? AND status = 'CONFIRMED'", Long.class, keyPattern);
    }

    private String orderStatus(String key) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM orders WHERE idempotency_key = ?", String.class, key);
    }

    private BigDecimal totalAmount(String key) {
        return jdbcTemplate.queryForObject("SELECT total_amount FROM orders WHERE idempotency_key = ?", BigDecimal.class, key);
    }

    private Map<String, Object> orderItem(String key) {
        return jdbcTemplate.queryForMap(
                "SELECT oi.quantity, oi.unit_price FROM order_items oi "
                        + "JOIN orders o ON o.id = oi.order_id WHERE o.idempotency_key = ?", key);
    }

    private long outboxCount(String orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'OrderPlaced' AND aggregate_id = ?", Long.class, orderId);
    }

    private long outboxCountForKey(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id IN (SELECT id::text FROM orders WHERE idempotency_key = ?)",
                Long.class, key);
    }

    private String idempotencyStatus(String key) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM idempotency_keys WHERE key = ?", String.class, key);
    }
}
