package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.in.query.Page;
import com.ecommerce.marketplace.application.ports.in.query.PageRequest;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;
import io.vavr.control.Option;

/**
 * Output port for product persistence.
 *
 * <p>{@link #findBySku(SKU)} returns {@link Option} — a pure existence check, "not found" is not
 * a failure at the persistence boundary. The application layer (e.g. a query use case) is
 * responsible for turning an absent {@code Option} into {@code Either.left(new
 * Failure.ProductNotFound(sku))} when the caller's use case actually requires the product to
 * exist. Mutating operations return {@code Either<Failure, T>} because they can genuinely fail
 * for business reasons ({@link Failure.DuplicateSku}, {@link Failure.ConcurrentStockConflict}).</p>
 */
public interface ProductRepositoryPort {

    Option<Product> findBySku(SKU sku);

    Either<Failure, Product> save(Product product);

    /**
     * Idempotent upsert by SKU (US-17): re-applying the same payload converges to the same
     * state rather than duplicating rows or erroring. Implementations back this with an atomic
     * {@code INSERT ... ON CONFLICT (sku) DO UPDATE}, incrementing {@code version} manually so
     * optimistic locking for checkout stays correct.
     */
    Either<Failure, Product> upsertBySku(Product product);

    /**
     * Persists an edit to an existing product (US-11), re-applying every field of the already
     * re-validated {@code product} aggregate. The aggregate carries the {@code version} the editor
     * loaded, so implementations must run Hibernate's optimistic {@code @Version} check against it:
     * a concurrent edit that advanced the stored row yields
     * {@link Failure.ConcurrentStockConflict} (a lost update was prevented), and a SKU that no
     * longer identifies a live row yields {@link Failure.ProductNotFound}. This replaces the
     * earlier stock-only {@code updateStock} signature: the edit flow re-validates and persists the
     * whole aggregate, not just stock, so the port receives the full domain object rather than a
     * loose primitive triple.
     */
    Either<Failure, Product> update(Product product);

    Either<Failure, Page<Product>> search(Option<String> searchText, Option<Category> category, PageRequest pageRequest);

    Either<Failure, Void> softDelete(SKU sku);
}
