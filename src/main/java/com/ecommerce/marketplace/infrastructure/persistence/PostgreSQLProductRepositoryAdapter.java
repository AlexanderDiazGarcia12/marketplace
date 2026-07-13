package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.in.query.Page;
import com.ecommerce.marketplace.application.ports.in.query.PageRequest;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;
import io.vavr.control.Option;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;

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
 * yields {@link Failure.ProductNotFound}, making the delete idempotent-friendly. The remaining
 * port methods are owned by later stories and left unimplemented on purpose.</p>
 */
public final class PostgreSQLProductRepositoryAdapter implements ProductRepositoryPort {

    private static final String SKU_UNIQUE_CONSTRAINT = "uq_products_sku";

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

    @Override
    public Either<Failure, Product> upsertBySku(Product product) {
        throw new UnsupportedOperationException("Idempotent upsert-by-SKU is delivered by US-17");
    }

    @Override
    public Either<Failure, Page<Product>> search(Option<String> searchText, Option<Category> category, PageRequest pageRequest) {
        throw new UnsupportedOperationException("Catalog search is delivered by US-13");
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
