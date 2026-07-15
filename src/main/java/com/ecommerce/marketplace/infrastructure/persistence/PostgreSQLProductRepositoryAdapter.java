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
 * Spring Data JPA adapter for {@link ProductRepositoryPort} — the sole place where {@code products}
 * rows are read or written; maps every persistence type back to pure domain objects via
 * {@link ProductMapper} so no {@code @Entity} escapes this package.
 *
 * <p>{@link #update(Product)} is the only place in the hexagon that catches a persistence exception:
 * Hibernate's optimistic {@code @Version} check is translated into
 * {@link Failure.ConcurrentStockConflict} so the layers above stay exception-free.
 * {@link #softDelete(SKU)} stamps {@code deleted_at} on the managed row and lets dirty checking flush
 * a versioned UPDATE, so a concurrent in-flight edit can no longer resurrect the row; a SKU with no
 * live row yields {@link Failure.ProductNotFound}. {@link #search(Option, Option, PageRequest)} is a
 * native paginated query whose {@code ILIKE} predicate stays sargable, excluding soft-deleted rows.
 * {@link #upsertBySku(Product)} is an atomic {@code INSERT ... ON CONFLICT (sku) DO UPDATE} that
 * converges on identical redelivery and refuses to resurrect a soft-deleted row (surfacing
 * {@link Failure.ProductNotFound}).</p>
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
     * Checkout stock decrement with a bounded optimistic retry. The whole loop runs inside one
     * {@code transactionTemplate.execute} on the shared {@code REQUIRED}-propagation bean, so it joins
     * the ambient checkout transaction: a later failure in the same Unit of Work (a declined payment)
     * rolls this decrement back automatically, with no compensating stock logic. It is deliberately
     * not {@code REQUIRES_NEW}, which would keep the decrement durable even when the payment fails.
     *
     * <p>Conflict detection is a zero-row versioned native UPDATE, never a thrown
     * {@code OptimisticLockException}. Each attempt re-reads the live row, applies the pure
     * {@link Product#decreaseStock(int)} rule (an unsatisfiable quantity yields
     * {@link Failure.InsufficientStock}/{@link Failure.InvalidStock}, returned immediately), then
     * issues {@code UPDATE ... WHERE sku = ? AND version = ?} (see
     * {@link SpringDataProductJpaRepository#decreaseStockIfVersionMatches}). A concurrent writer that
     * advanced the version makes it match zero rows — a normal statement that neither aborts nor marks
     * the transaction rollback-only, so the retry can run in the same transaction (a managed
     * {@code merge}/{@code flush} conflict would poison it instead). Each retry first calls
     * {@code entityManager.clear()} so the re-read observes the winner's committed version under
     * {@code READ COMMITTED}. After {@value #MAX_DECREASE_ATTEMPTS} attempts the conflict is returned
     * as-is.</p>
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
