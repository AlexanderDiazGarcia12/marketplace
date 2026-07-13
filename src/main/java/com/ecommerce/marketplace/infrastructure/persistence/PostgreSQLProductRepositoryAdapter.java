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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Spring Data JPA adapter for {@link ProductRepositoryPort}. The sole place where {@code products}
 * rows are read/written; it maps every persistence type back to pure domain objects via
 * {@link ProductMapper} so no {@code @Entity} escapes {@code infrastructure.persistence}.
 *
 * <p>US-09 exercises {@link #save(Product)} and {@link #findBySku(SKU)}. The remaining port
 * methods are intentionally left unimplemented — each is owned by a later story and building a
 * fake here would be worse than an honest signal that it is not wired yet.</p>
 */
public final class PostgreSQLProductRepositoryAdapter implements ProductRepositoryPort {

    private static final String SKU_UNIQUE_CONSTRAINT = "uq_products_sku";

    private final SpringDataProductJpaRepository jpaRepository;

    public PostgreSQLProductRepositoryAdapter(SpringDataProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
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
    public Either<Failure, Product> upsertBySku(Product product) {
        throw new UnsupportedOperationException("Idempotent upsert-by-SKU is delivered by US-17");
    }

    @Override
    public Either<Failure, Product> updateStock(SKU sku, int newStock, long expectedVersion) {
        throw new UnsupportedOperationException("Optimistic stock update is delivered by US-11");
    }

    @Override
    public Either<Failure, Page<Product>> search(Option<String> searchText, Option<Category> category, PageRequest pageRequest) {
        throw new UnsupportedOperationException("Catalog search is delivered by US-13");
    }

    @Override
    public Either<Failure, Void> softDelete(SKU sku) {
        throw new UnsupportedOperationException("Soft delete is delivered by US-12");
    }

    private static boolean isSkuUniqueViolation(DataIntegrityViolationException violation) {
        return violation.getCause() instanceof ConstraintViolationException cause
                && SKU_UNIQUE_CONSTRAINT.equalsIgnoreCase(cause.getConstraintName());
    }

    private static Either<Failure, Product> rethrow(DataIntegrityViolationException violation) {
        throw violation;
    }
}
