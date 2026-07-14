package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.application.ports.query.PageRequest;
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

    /**
     * Atomically decrements a live product's stock by {@code quantity} under optimistic locking,
     * for the checkout Unit of Work (US-22). Distinct from {@link #update(Product)} on purpose: a
     * concurrent stock change here is a normal, expected race that the implementation retries a
     * bounded number of times (the buyer just lost a version bump, not an edit worth surfacing),
     * whereas {@code update} must surface a conflict immediately to the editing admin rather than
     * silently retry-and-clobber. On success returns the persisted {@link Product} at its new stock
     * and bumped version; failures are {@link Failure.InsufficientStock} / {@link Failure.InvalidStock}
     * (the requested quantity is impossible — never retried), {@link Failure.ProductNotFound} (no
     * live row for the SKU) or {@link Failure.ConcurrentStockConflict} (the bounded retry was
     * exhausted). The decrement joins the caller's ambient transaction, so a later failure in the
     * same checkout (a declined payment) rolls the stock back automatically with no compensation.
     */
    Either<Failure, Product> decreaseStock(SKU sku, int quantity);

    Either<Failure, Page<Product>> search(Option<String> searchText, Option<Category> category, PageRequest pageRequest);

    Either<Failure, Void> softDelete(SKU sku);
}
