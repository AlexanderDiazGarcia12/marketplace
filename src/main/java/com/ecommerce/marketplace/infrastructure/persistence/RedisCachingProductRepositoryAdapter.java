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
 * Read-through Redis cache decorator over the real {@link ProductRepositoryPort} (US-14). Wired
 * only under the {@code cache} profile; without it the plain Postgres adapter is injected and this
 * class is never instantiated. The application use cases ({@code GetProductService},
 * {@code SearchProductService}) depend solely on {@link ProductRepositoryPort} and are unaware a
 * cache exists — the decorator is a pure infrastructure concern, honoring the CA and RNF-2.
 *
 * <p><strong>What is cached:</strong> the two read operations, {@link #findBySku(SKU)} and
 * {@link #search(Option, Option, PageRequest)}. Writes ({@link #save}, {@link #update},
 * {@link #softDelete}) delegate to the delegate and, only on success, invalidate the cache. Reads
 * degrade gracefully: any Redis error (miss, timeout, unreachable) falls back to the delegate, so
 * a Redis outage never breaks the request path — it only removes the cache benefit.</p>
 *
 * <p><strong>Search invalidation strategy — version-prefixed keys.</strong> A single write can
 * change which products land on any cached filter/page combination, so surgical per-key eviction
 * is impossible. Rather than {@code SCAN}/{@code DEL} the whole search namespace on every write
 * (extra round-trips) or {@code KEYS} (blocking, forbidden in production), search keys embed a
 * monotonically increasing version read from {@code product:search:version}. A write bumps that
 * counter with a single atomic {@code INCR}; every previously cached search key instantly becomes
 * unreachable and is reclaimed by its TTL. This favors correctness over hit-rate, appropriate for
 * an optional Could-sized story.</p>
 *
 * <p><strong>TTL:</strong> {@value #TTL_SECONDS}s on every cached entry, as a safety net against
 * any invalidation gap (e.g. a write that failed to bump the version after a partial Redis error).
 * Short enough that stale reads self-heal within a minute, long enough to absorb the repeated-read
 * bursts this cache targets.</p>
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
