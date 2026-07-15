package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.application.ports.query.PageRequest;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Read-through Redis cache decorator over the real {@link ProductRepositoryPort}, wired only under
 * the {@code cache} profile. The application use cases depend solely on {@link ProductRepositoryPort}
 * and are unaware a cache exists — the decorator is a pure infrastructure concern. Reads
 * ({@link #findBySku(SKU)}, {@link #search(Option, Option, PageRequest)}) are cached; writes delegate
 * and, only on success, invalidate. Any Redis error falls back to the delegate, so an outage removes
 * the cache benefit rather than breaking the request path.
 *
 * <p>Search results cannot be evicted surgically (one write can change any cached filter/page), so
 * search keys embed a version counter read from {@code product:search:version}; a write bumps it with
 * a single atomic {@code INCR}, making every previously cached search key unreachable at once —
 * favoring correctness over hit-rate. Every entry also carries a {@value #TTL_SECONDS}s TTL as a
 * safety net so any invalidation gap self-heals.</p>
 */
public final class RedisCachingProductRepositoryAdapter implements ProductRepositoryPort {

    private static final long TTL_SECONDS = 60;
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);
    private static final String PRODUCT_KEY_PREFIX = "product:sku:";
    private static final String SEARCH_KEY_PREFIX = "product:search:";
    private static final String SEARCH_VERSION_KEY = "product:search:version";

    private final ProductRepositoryPort delegate;
    private final StringRedisTemplate redis;
    private final ProductCacheCodec codec;

    public RedisCachingProductRepositoryAdapter(
            ProductRepositoryPort delegate,
            StringRedisTemplate redis,
            ProductCacheCodec codec) {
        this.delegate = delegate;
        this.redis = redis;
        this.codec = codec;
    }

    @Override
    public Option<Product> findBySku(SKU sku) {
        String key = productKey(sku);
        return cachedProduct(key)
                .orElse(() -> loadAndCacheProduct(key, sku));
    }

    private Option<Product> cachedProduct(String key) {
        return read(key).flatMap(codec::decodeProduct);
    }

    private Option<Product> loadAndCacheProduct(String key, SKU sku) {
        Option<Product> loaded = delegate.findBySku(sku);
        loaded.flatMap(codec::encodeProduct).forEach(json -> write(key, json));
        return loaded;
    }

    @Override
    public Either<Failure, Page<Product>> search(Option<String> searchText, Option<Category> category, PageRequest pageRequest) {
        String key = searchKey(searchText, category, pageRequest);
        return cachedPage(key)
                .<Either<Failure, Page<Product>>>map(Either::right)
                .getOrElse(() -> loadAndCachePage(key, searchText, category, pageRequest));
    }

    private Option<Page<Product>> cachedPage(String key) {
        return read(key).flatMap(codec::decodePage);
    }

    private Either<Failure, Page<Product>> loadAndCachePage(
            String key, Option<String> searchText, Option<Category> category, PageRequest pageRequest) {
        Either<Failure, Page<Product>> result = delegate.search(searchText, category, pageRequest);
        result.forEach(page -> codec.encodePage(page).forEach(json -> write(key, json)));
        return result;
    }

    @Override
    public Either<Failure, Product> save(Product product) {
        return delegate.save(product).peek(this::invalidate);
    }

    @Override
    public Either<Failure, Product> update(Product product) {
        return delegate.update(product).peek(this::invalidate);
    }

    @Override
    public Either<Failure, Product> upsertBySku(Product product) {
        return delegate.upsertBySku(product).peek(this::invalidate);
    }

    @Override
    public Either<Failure, Product> decreaseStock(SKU sku, int quantity) {
        return delegate.decreaseStock(sku, quantity).peek(this::invalidate);
    }

    @Override
    public Either<Failure, Void> softDelete(SKU sku) {
        return delegate.softDelete(sku).peek(ignored -> invalidate(sku));
    }

    private void invalidate(Product product) {
        invalidate(product.sku());
    }

    private void invalidate(SKU sku) {
        Try.run(() -> {
            redis.delete(productKey(sku));
            redis.opsForValue().increment(SEARCH_VERSION_KEY);
        });
    }

    private Option<String> read(String key) {
        return Try.of(() -> redis.opsForValue().get(key)).toOption().flatMap(Option::of);
    }

    private void write(String key, String json) {
        Try.run(() -> redis.opsForValue().set(key, json, TTL));
    }

    private static String productKey(SKU sku) {
        return PRODUCT_KEY_PREFIX + sku.value();
    }

    private String searchKey(Option<String> searchText, Option<Category> category, PageRequest pageRequest) {
        String text = searchText.map(String::trim).filter(value -> !value.isEmpty()).getOrElse("");
        String categoryName = category.map(Category::name).getOrElse("");
        return SEARCH_KEY_PREFIX + currentSearchVersion()
                + ":text=" + text
                + ":category=" + categoryName
                + ":page=" + pageRequest.page()
                + ":size=" + pageRequest.size();
    }

    private long currentSearchVersion() {
        return read(SEARCH_VERSION_KEY).flatMap(value -> Try.of(() -> Long.parseLong(value)).toOption()).getOrElse(0L);
    }
}
