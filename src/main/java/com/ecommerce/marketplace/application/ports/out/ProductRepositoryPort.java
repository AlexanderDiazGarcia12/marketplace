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
     * Idempotent upsert by SKU: re-applying the same payload converges to the same state rather
     * than duplicating rows or erroring. Implementations back this with an atomic
     * {@code INSERT ... ON CONFLICT (sku) DO UPDATE}, incrementing {@code version} manually so
     * optimistic locking for checkout stays correct.
     */
    Either<Failure, Product> upsertBySku(Product product);

    /**
     * Persists an edit to an already re-validated product aggregate, which carries the {@code version}
     * the editor loaded, so implementations run Hibernate's optimistic {@code @Version} check: a
     * concurrent edit that advanced the stored row yields {@link Failure.ConcurrentStockConflict}
     * (lost update prevented), and a SKU that no longer identifies a live row yields
     * {@link Failure.ProductNotFound}.
     */
    Either<Failure, Product> update(Product product);

    /**
     * Atomically decrements a live product's stock by {@code quantity} under optimistic locking for
     * the checkout Unit of Work. Distinct from {@link #update(Product)}: a concurrent stock change
     * here is an expected race the implementation retries a bounded number of times, whereas
     * {@code update} must surface a conflict immediately rather than retry-and-clobber. Failures are
     * {@link Failure.InsufficientStock} / {@link Failure.InvalidStock} (impossible quantity, never
     * retried), {@link Failure.ProductNotFound} (no live row) or {@link Failure.ConcurrentStockConflict}
     * (bounded retry exhausted). The decrement joins the caller's ambient transaction, so a later
     * checkout failure rolls the stock back automatically.
     */
    Either<Failure, Product> decreaseStock(SKU sku, int quantity);

    Either<Failure, Page<Product>> search(Option<String> searchText, Option<Category> category, PageRequest pageRequest);

    Either<Failure, Void> softDelete(SKU sku);
}
