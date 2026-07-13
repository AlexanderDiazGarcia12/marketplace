package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.application.ports.query.PageRequest;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import com.ecommerce.marketplace.domain.model.product.Weight;
import io.vavr.collection.List;
import io.vavr.control.Try;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

/**
 * Serializes domain products to/from a compact JSON representation for the Redis read cache
 * (US-14). It never serializes domain records directly: those carry Vavr types and validated
 * value objects Jackson cannot round-trip safely. Instead it maps to infrastructure-owned flat
 * DTOs ({@link ProductSnapshot}, {@link PageSnapshot}) built from plain Java types, keeping the
 * cache format an implementation detail of {@code infrastructure.persistence} and never leaking
 * a Spring or Jackson type across the hexagon boundary.
 *
 * <p>Decoding rebuilds value objects through their validating constructors, so a corrupt or
 * schema-drifted cache entry surfaces as {@link Option#none()} (treated as a miss by the caching
 * adapter) rather than a poisoned domain object.</p>
 */
public final class ProductCacheCodec {

    private final ObjectMapper objectMapper;

    public ProductCacheCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    io.vavr.control.Option<String> encodeProduct(Product product) {
        return Try.of(() -> objectMapper.writeValueAsString(ProductSnapshot.from(product))).toOption();
    }

    io.vavr.control.Option<Product> decodeProduct(String json) {
        return Try.of(() -> objectMapper.readValue(json, ProductSnapshot.class))
                .toOption()
                .flatMap(ProductSnapshot::toDomain);
    }

    io.vavr.control.Option<String> encodePage(Page<Product> page) {
        return Try.of(() -> objectMapper.writeValueAsString(PageSnapshot.from(page))).toOption();
    }

    io.vavr.control.Option<Page<Product>> decodePage(String json) {
        return Try.of(() -> objectMapper.readValue(json, PageSnapshot.class))
                .toOption()
                .flatMap(PageSnapshot::toDomain);
    }

    record ProductSnapshot(
            String sku,
            String name,
            String description,
            String category,
            BigDecimal price,
            int stock,
            BigDecimal weightKg,
            long version) {

        static ProductSnapshot from(Product product) {
            return new ProductSnapshot(
                    product.sku().value(),
                    product.name(),
                    product.description(),
                    product.category().name(),
                    product.price().amount(),
                    product.stock(),
                    product.weight().kilograms(),
                    product.version());
        }

        io.vavr.control.Option<Product> toDomain() {
            return Try.of(() -> new Product(
                    new SKU(sku),
                    name,
                    description,
                    Category.valueOf(category),
                    new Money(price),
                    stock,
                    new Weight(weightKg),
                    version)).toOption();
        }
    }

    record PageSnapshot(java.util.List<ProductSnapshot> content, int page, int size, long totalElements) {

        static PageSnapshot from(Page<Product> page) {
            return new PageSnapshot(
                    page.content().map(ProductSnapshot::from).toJavaList(),
                    page.page(),
                    page.size(),
                    page.totalElements());
        }

        io.vavr.control.Option<Page<Product>> toDomain() {
            io.vavr.control.Option<List<Product>> decoded = List.ofAll(content)
                    .foldLeft(io.vavr.control.Option.of(List.<Product>empty()),
                            (accumulated, snapshot) -> accumulated.flatMap(
                                    products -> snapshot.toDomain().map(products::append)));
            return decoded.map(products -> new Page<>(products, page, size, totalElements));
        }
    }
}
