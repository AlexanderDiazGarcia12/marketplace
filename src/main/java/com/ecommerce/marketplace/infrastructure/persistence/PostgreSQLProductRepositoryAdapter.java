package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.application.ports.query.PageRequest;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spring Data JPA adapter for {@link ProductRepositoryPort}. The sole place where {@code products}
 * rows are read/written; it maps every persistence type back to pure domain objects via
 * {@link ProductMapper} so no {@code @Entity} escapes {@code infrastructure.persistence}.
 *
 * <p>US-09 exercises {@link #save(Product)} and {@link #findBySku(SKU)}. US-11 adds
 * {@link #update(Product)}: the <em>only</em> place in the whole hexagon that catches a persistence
 * exception. Hibernate's optimistic {@code @Version} check is translated here into
 * {@code Either.left(new Failure.ConcurrentStockConflict(sku))} — the business flow above this
 * adapter (application, web) stays exception-free and pattern-matches the {@code Failure} value.
 *
 * <p>US-12 adds {@link #softDelete(SKU)}: it loads the managed row (via {@code findBySku}, which
 * already filters {@code deleted_at IS NULL}), stamps {@code deleted_at} in place and lets
 * Hibernate's dirty checking flush a versioned UPDATE — advancing {@code @Version} so a concurrent
 * in-flight edit can no longer resurrect the row (its stale-version merge fails the optimistic
 * check instead). A SKU that no longer identifies a live row (never existed, or already deleted)
 * yields {@link Failure.ProductNotFound}, making the delete idempotent-friendly.
 *
 * <p>US-13 adds {@link #search(Option, Option, PageRequest)}: a native paginated query whose
 * {@code ILIKE '%term%'} predicate stays sargable so the trgm GIN indexes from US-09 remain usable
 * (see {@link SpringDataProductJpaRepository#search}). A separate count query supplies
 * {@code totalElements}; both filters are optional and soft-deleted rows are excluded.
 *
 * <p>US-17 adds {@link #upsertBySku(Product)}: an atomic native {@code INSERT ... ON CONFLICT (sku)
 * DO UPDATE} (see {@link SpringDataProductJpaRepository#upsertBySku}) that converges to the same
 * state on identical re-delivery (Kafka at-least-once) instead of duplicating or erroring. It bumps
 * {@code version = products.version + 1} in SQL, deliberately bypassing Hibernate's {@code @Version}
 * so checkout's optimistic locking is never invalidated. A conflict on a soft-deleted row is
 * refused (the DO UPDATE is guarded by {@code deleted_at IS NULL}), surfacing an empty {@code
 * RETURNING} that this adapter maps to {@link Failure.ProductNotFound} — an automated import does
 * not resurrect a deliberately deleted product.</p>
 */
public final class PostgreSQLProductRepositoryAdapter implements ProductRepositoryPort {

    private static final String SKU_UNIQUE_CONSTRAINT = "uq_products_sku";
    private static final int MAX_DECREASE_ATTEMPTS = 3;
    private static final int BACKOFF_BASE_MS = 20;
    private static final int BACKOFF_JITTER_MS = 30;

    private final SpringDataProductJpaRepository jpaRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public PostgreSQLProductRepositoryAdapter(
            SpringDataProductJpaRepository jpaRepository,
            EntityManager entityManager,
            TransactionTemplate transactionTemplate) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Option<Product> findBySku(SKU sku) {
        return Option.ofOptional(jpaRepository.findBySku(sku.value()))
                .map(ProductMapper::toDomain);
    }

    @Override
    public Either<Failure, Product> save(Product product) {
        try {
            JPAProductEntity persisted = jpaRepository.saveAndFlush(ProductMapper.toEntity(product));
            return Either.right(ProductMapper.toDomain(persisted));
        } catch (DataIntegrityViolationException violation) {
            return isSkuUniqueViolation(violation)
                    ? Either.left(new Failure.DuplicateSku(product.sku()))
                    : rethrow(violation);
        }
    }

    @Override
    public Either<Failure, Product> update(Product product) {
        try {
            return transactionTemplate.execute(status -> applyEdit(product));
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException conflict) {
            return Either.left(new Failure.ConcurrentStockConflict(product.sku()));
        }
    }

    private Either<Failure, Product> applyEdit(Product product) {
        return Option.ofOptional(jpaRepository.findBySku(product.sku().value()))
                .map(managed -> mergeEdit(managed, product))
                .getOrElse(() -> Either.left(new Failure.ProductNotFound(product.sku())));
    }

    private Either<Failure, Product> mergeEdit(JPAProductEntity managed, Product product) {
        JPAProductEntity edited = ProductMapper.toEditedEntity(managed, product);
        entityManager.detach(managed);
        JPAProductEntity merged = entityManager.merge(edited);
        entityManager.flush();
        return Either.right(ProductMapper.toDomain(merged));
    }

    /**
     * Checkout stock decrement with a bounded optimistic retry (US-22). The whole retry loop runs
     * inside <em>one</em> {@code transactionTemplate.execute} on the shared
     * {@code REQUIRED}-propagation bean, so it joins the ambient checkout transaction: a later
     * failure in the same Unit of Work (a declined payment) rolls this decrement back automatically,
     * with no compensating stock logic. Deliberately not {@code REQUIRES_NEW}: an independent
     * committing transaction would keep the decrement durable even when the payment later fails,
     * breaking the CA's "rollback restores stock automatically" design.
     *
     * <p><strong>Conflict detection is a zero-row native UPDATE, never a thrown
     * {@code OptimisticLockException}.</strong> Each attempt re-reads the live row, applies the pure
     * {@link Product#decreaseStock(int)} rule (a quantity the stock cannot satisfy yields
     * {@link Failure.InsufficientStock}/{@link Failure.InvalidStock} — returned immediately, never
     * retried), then issues a versioned {@code UPDATE ... SET stock = stock - ?, version = version + 1
     * WHERE sku = ? AND version = ?} (see
     * {@link SpringDataProductJpaRepository#decreaseStockIfVersionMatches}). A concurrent writer that
     * advanced the version makes that statement match <em>zero rows</em> — a normal, successful
     * statement that neither aborts the transaction nor marks it rollback-only. That is the crucial
     * difference from a managed {@code merge}/{@code flush}: its {@code OptimisticLockException} marks
     * the whole transaction rollback-only per the JPA spec, so an in-transaction retry is impossible
     * (the eventual {@code commit} throws {@code UnexpectedRollbackException} — proven by
     * {@code PurchaseProductServiceIT}). The zero-row result maps to
     * {@link Failure.ConcurrentStockConflict} and is retried within the same transaction. Each retry
     * first calls {@code entityManager.clear()}: a JPQL {@code findBySku} would otherwise return the
     * row still managed in the persistence context from the previous attempt (Hibernate keeps the
     * managed instance and ignores the freshly-read columns), so the re-read must start from an empty
     * context to observe the winner's committed version under {@code READ COMMITTED}. After
     * {@value #MAX_DECREASE_ATTEMPTS} exhausted attempts the conflict is returned as-is. The whole
     * flow is exception-free — there is no try/catch anywhere in the checkout story.</p>
     */
    @Override
    public Either<Failure, Product> decreaseStock(SKU sku, int quantity) {
        return transactionTemplate.execute(status -> attemptDecrease(sku, quantity));
    }

    private Either<Failure, Product> attemptDecrease(SKU sku, int quantity) {
        Either<Failure, Product> outcome = decreaseOnce(sku, quantity);
        for (int attempt = 2; attempt <= MAX_DECREASE_ATTEMPTS && isConflict(outcome); attempt++) {
            backoff();
            entityManager.clear();
            outcome = decreaseOnce(sku, quantity);
        }
        return outcome;
    }

    private Either<Failure, Product> decreaseOnce(SKU sku, int quantity) {
        return Option.ofOptional(jpaRepository.findBySku(sku.value()))
                .toEither(() -> (Failure) new Failure.ProductNotFound(sku))
                .map(ProductMapper::toDomain)
                .flatMap(current -> ensureSufficientStock(current, quantity)
                        .flatMap(expectedVersion -> versionedDecrement(sku, quantity, expectedVersion)));
    }

    private static Either<Failure, Long> ensureSufficientStock(Product current, int quantity) {
        return current.decreaseStock(quantity).map(validated -> current.version());
    }

    private Either<Failure, Product> versionedDecrement(SKU sku, int quantity, long expectedVersion) {
        return io.vavr.collection.List.ofAll(
                        jpaRepository.decreaseStockIfVersionMatches(sku.value(), quantity, Math.toIntExact(expectedVersion)))
                .headOption()
                .map(ProductMapper::toDomain)
                .<Either<Failure, Product>>map(Either::right)
                .getOrElse(() -> Either.left(new Failure.ConcurrentStockConflict(sku)));
    }

    private static boolean isConflict(Either<Failure, Product> outcome) {
        return outcome.isLeft() && outcome.getLeft() instanceof Failure.ConcurrentStockConflict;
    }

    private static void backoff() {
        Try.run(() -> Thread.sleep(BACKOFF_BASE_MS + ThreadLocalRandom.current().nextInt(BACKOFF_JITTER_MS)))
                .onFailure(interrupted -> Thread.currentThread().interrupt());
    }

    @Override
    public Either<Failure, Product> upsertBySku(Product product) {
        return transactionTemplate.execute(status -> applyUpsert(product));
    }

    private Either<Failure, Product> applyUpsert(Product product) {
        return io.vavr.collection.List.ofAll(jpaRepository.upsertBySku(
                        product.sku().value(),
                        product.name(),
                        product.description(),
                        product.category().name(),
                        product.price().amount(),
                        product.stock(),
                        product.weight().kilograms()))
                .headOption()
                .map(ProductMapper::toDomain)
                .map(Either::<Failure, Product>right)
                .getOrElse(() -> Either.left(new Failure.ProductNotFound(product.sku())));
    }

    @Override
    public Either<Failure, Page<Product>> search(Option<String> searchText, Option<Category> category, PageRequest pageRequest) {
        String text = searchText.map(String::trim).filter(value -> !value.isEmpty()).getOrNull();
        String categoryName = category.map(Category::name).getOrNull();

        Seq<Product> content = io.vavr.collection.List.ofAll(
                        jpaRepository.search(text, categoryName, toPageable(pageRequest)))
                .map(ProductMapper::toDomain);
        long total = jpaRepository.countSearch(text, categoryName);

        return Either.right(Page.of(content, pageRequest, total));
    }

    private static org.springframework.data.domain.Pageable toPageable(PageRequest pageRequest) {
        return org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size());
    }

    @Override
    public Either<Failure, Void> softDelete(SKU sku) {
        return transactionTemplate.execute(status -> applySoftDelete(sku));
    }

    private Either<Failure, Void> applySoftDelete(SKU sku) {
        return Option.ofOptional(jpaRepository.findBySku(sku.value()))
                .map(this::stampDeleted)
                .getOrElse(() -> Either.left(new Failure.ProductNotFound(sku)));
    }

    private Either<Failure, Void> stampDeleted(JPAProductEntity managed) {
        managed.markDeleted(OffsetDateTime.now());
        entityManager.flush();
        return Either.right(null);
    }

    private static boolean isSkuUniqueViolation(DataIntegrityViolationException violation) {
        return violation.getCause() instanceof ConstraintViolationException cause
                && SKU_UNIQUE_CONSTRAINT.equalsIgnoreCase(cause.getConstraintName());
    }

    private static Either<Failure, Product> rethrow(DataIntegrityViolationException violation) {
        throw violation;
    }
}
