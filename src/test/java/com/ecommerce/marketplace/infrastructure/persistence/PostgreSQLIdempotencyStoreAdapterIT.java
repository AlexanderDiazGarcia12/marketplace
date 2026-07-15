package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.out.IdempotencyRecord;
import com.ecommerce.marketplace.application.ports.out.IdempotencyRecord.IdempotencyStatus;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import io.vavr.control.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link PostgreSQLIdempotencyStoreAdapter} against the real Docker Compose
 * Postgres. Exercises the four decision-tree branches plus the concurrent same-key race the
 * primary key must resolve.
 *
 * <p>{@code @Transactional(NOT_SUPPORTED)} disables {@code @DataJpaTest}'s shared per-method
 * rollback: otherwise two sequential {@code begin()} calls would share one Hibernate session and
 * the second INSERT would raise an in-memory {@code NonUniqueObjectException} before the real
 * {@code idempotency_keys_pkey} constraint is exercised. Every call thus gets its own transaction
 * and commits for real, matching independent HTTP requests; {@link #cleanUp()} deletes each key
 * afterwards.</p>
 *
 * <p>{@link #replayOfCompletedKeyInsideCallersAmbientTransactionCommitsWithoutRollback()} and
 * {@link #twoBeginsForSameNewKeyInsideOneAmbientTransactionStillResolveByPk()} cover a caller that
 * wraps {@code begin()} in its own business transaction, proving {@code insertFresh}'s
 * {@code REQUIRES_NEW} keeps a losing INSERT's rollback local instead of marking the caller's
 * transaction rollback-only.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.docker.compose.skip.in-tests=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgreSQLIdempotencyStoreAdapterIT {

    @Autowired
    private SpringDataIdempotencyKeyJpaRepository jpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private IdempotencyKey currentKey;

    @AfterEach
    void cleanUp() {
        if (currentKey != null) {
            deleteKey(currentKey);
        }
    }

    private PostgreSQLIdempotencyStoreAdapter adapter() {
        return new PostgreSQLIdempotencyStoreAdapter(jpaRepository, transactionManager);
    }

    @Test
    void newKeyIsInsertedInProgressAndReturnedFresh() {
        IdempotencyKey key = freshKey();

        Either<Failure, IdempotencyRecord> result = adapter().begin(key, "hash-a");

        assertThat(result.isRight()).isTrue();
        IdempotencyRecord record = result.get();
        assertThat(record.key()).isEqualTo(key);
        assertThat(record.requestHash()).isEqualTo("hash-a");
        assertThat(record.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(record.responseSnapshot()).isNull();
    }

    @Test
    void retryWhileInProgressWithSameHashIsDuplicateOrderRequest() {
        IdempotencyKey key = freshKey();
        adapter().begin(key, "hash-a");

        Either<Failure, IdempotencyRecord> retry = adapter().begin(key, "hash-a");

        assertThat(retry.isLeft()).isTrue();
        assertThat(retry.getLeft()).isEqualTo(new Failure.DuplicateOrderRequest(key));
    }

    @Test
    void retryAfterCompletionWithSameHashReturnsStoredSnapshotWithoutReexecuting() {
        IdempotencyKey key = freshKey();
        PostgreSQLIdempotencyStoreAdapter adapter = adapter();
        adapter.begin(key, "hash-a");
        String snapshot = "{\"orderId\":\"o-1\",\"status\":\"CONFIRMED\"}";
        adapter.complete(key, snapshot);

        Either<Failure, IdempotencyRecord> retry = adapter.begin(key, "hash-a");

        assertThat(retry.isRight()).isTrue();
        IdempotencyRecord record = retry.get();
        assertThat(record.status()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertSameJson(snapshot, record.responseSnapshot());
    }

    @Test
    void differentHashForInProgressKeyIsMismatchRegardlessOfStatus() {
        IdempotencyKey key = freshKey();
        adapter().begin(key, "hash-a");

        Either<Failure, IdempotencyRecord> mismatch = adapter().begin(key, "hash-b");

        assertThat(mismatch.isLeft()).isTrue();
        assertThat(mismatch.getLeft()).isEqualTo(new Failure.IdempotencyKeyMismatch(key));
    }

    @Test
    void differentHashForCompletedKeyIsMismatchAndTakesPriorityOverSnapshotReplay() {
        IdempotencyKey key = freshKey();
        PostgreSQLIdempotencyStoreAdapter adapter = adapter();
        adapter.begin(key, "hash-a");
        adapter.complete(key, "{\"orderId\":\"o-1\"}");

        Either<Failure, IdempotencyRecord> mismatch = adapter.begin(key, "hash-b");

        assertThat(mismatch.isLeft()).isTrue();
        assertThat(mismatch.getLeft()).isEqualTo(new Failure.IdempotencyKeyMismatch(key));
    }

    @Test
    void completeIsGuardedSoASecondCompletionKeepsTheFirstSnapshot() {
        IdempotencyKey key = freshKey();
        PostgreSQLIdempotencyStoreAdapter adapter = adapter();
        adapter.begin(key, "hash-a");
        adapter.complete(key, "{\"orderId\":\"first\"}");

        Either<Failure, IdempotencyRecord> second = adapter.complete(key, "{\"orderId\":\"second\"}");

        assertThat(second.isRight()).isTrue();
        assertSameJson("{\"orderId\":\"first\"}", second.get().responseSnapshot());
        assertThat(second.get().status()).isEqualTo(IdempotencyStatus.COMPLETED);
    }

    @Test
    void replayOfCompletedKeyInsideCallersAmbientTransactionCommitsWithoutRollback() {
        IdempotencyKey key = freshKey();
        PostgreSQLIdempotencyStoreAdapter adapter = adapter();
        adapter.begin(key, "hash-a");
        adapter.complete(key, "{\"orderId\":\"o-1\"}");

        TransactionTemplate callerTransaction = new TransactionTemplate(transactionManager);
        Either<Failure, IdempotencyRecord> replay =
                callerTransaction.execute(status -> adapter.begin(key, "hash-a"));

        assertThat(replay.isRight()).isTrue();
        assertThat(replay.get().status()).isEqualTo(IdempotencyStatus.COMPLETED);
    }

    @Test
    void twoBeginsForSameNewKeyInsideOneAmbientTransactionStillResolveByPk() {
        IdempotencyKey key = freshKey();
        PostgreSQLIdempotencyStoreAdapter adapter = adapter();

        TransactionTemplate callerTransaction = new TransactionTemplate(transactionManager);
        List<Either<Failure, IdempotencyRecord>> outcomes = callerTransaction.execute(status ->
                List.of(adapter.begin(key, "hash-a"), adapter.begin(key, "hash-a")));

        assertThat(outcomes.get(0).isRight()).isTrue();
        assertThat(outcomes.get(1).isLeft()).isTrue();
        assertThat(outcomes.get(1).getLeft()).isEqualTo(new Failure.DuplicateOrderRequest(key));
    }

    @Test
    void twoConcurrentBeginsForTheSameNewKeyLeaveExactlyOneWinner() throws Exception {
        IdempotencyKey key = freshKey();
        try {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch startLine = new CountDownLatch(1);

            Future<Either<Failure, IdempotencyRecord>> first = pool.submit(() -> racingBegin(key, startLine));
            Future<Either<Failure, IdempotencyRecord>> second = pool.submit(() -> racingBegin(key, startLine));
            startLine.countDown();

            Either<Failure, IdempotencyRecord> a = first.get(10, TimeUnit.SECONDS);
            Either<Failure, IdempotencyRecord> b = second.get(10, TimeUnit.SECONDS);
            pool.shutdown();

            long winners = countRight(a, b);
            long losers = countDuplicate(a, b);
            assertThat(winners).isEqualTo(1L);
            assertThat(losers).isEqualTo(1L);
            assertThat(jpaRepository.findById(key.value())).isPresent();
        } finally {
            deleteKey(key);
        }
    }

    private Either<Failure, IdempotencyRecord> racingBegin(
            IdempotencyKey key, CountDownLatch startLine) throws InterruptedException {
        startLine.await();
        return adapter().begin(key, "hash-a");
    }

    @SafeVarargs
    private static long countRight(Either<Failure, IdempotencyRecord>... results) {
        return Arrays.stream(results).filter(Either::isRight).count();
    }

    @SafeVarargs
    private static long countDuplicate(Either<Failure, IdempotencyRecord>... results) {
        return Arrays.stream(results)
                .filter(Either::isLeft)
                .map(Either::getLeft)
                .filter(failure -> failure instanceof Failure.DuplicateOrderRequest)
                .count();
    }

    private void deleteKey(IdempotencyKey key) {
        jdbcTemplate.update("DELETE FROM idempotency_keys WHERE key = ?", key.value());
    }

    /**
     * Postgres normalizes {@code jsonb} on write (whitespace and key order are not preserved), so
     * a round-tripped snapshot never string-equals the literal it was stored with. Comparing parsed
     * trees instead asserts what the adapter actually promises: the same JSON document, not the
     * same bytes.
     */
    private static void assertSameJson(String expected, String actual) {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.readTree(actual)).isEqualTo(mapper.readTree(expected));
    }

    private IdempotencyKey freshKey() {
        currentKey = new IdempotencyKey("it-" + UUID.randomUUID());
        return currentKey;
    }
}
