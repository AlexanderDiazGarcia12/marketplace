package com.ecommerce.marketplace.infrastructure.persistence;

import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import com.ecommerce.marketplace.domain.model.product.Weight;

/**
 * Explicit bidirectional Data Mapper between the pure {@link Product} aggregate and the
 * {@link JPAProductEntity} persistence row. Keeps Hibernate types out of the hexagon: the domain
 * never sees {@code @Entity} classes and the entity never sees itself leak to a view.
 *
 * <p>Value objects are reconstructed through their canonical constructors ({@code new SKU(...)},
 * {@code new Money(...)}, {@code new Weight(...)}) rather than their {@code Either}-returning
 * {@code of(...)} factories: a row already in the database is, by the DB CHECK constraints and by
 * the fact it was only ever written through validated aggregates, guaranteed well-formed, so there
 * is no {@code Failure} to report here — the constructors still guard the invariant, they just
 * never need to surface a rejection.</p>
 */
final class ProductMapper {

    private ProductMapper() {
    }

    static JPAProductEntity toEntity(Product product) {
        return new JPAProductEntity(
                product.sku().value(),
                product.name(),
                product.description(),
                product.category(),
                product.price().amount(),
                product.stock(),
                product.weight().kilograms(),
                Math.toIntExact(product.version()));
    }

    /**
     * Projects the re-validated {@code product}'s editable fields onto a detached row that reuses
     * {@code managed}'s identity, so a merge runs Hibernate's versioned UPDATE (US-11). The
     * {@code version} carried is the one the editor loaded on {@code product} (its expected
     * version), not {@code managed}'s freshly-read one, which is what makes a concurrent edit
     * surface as an optimistic-lock failure instead of overwriting the newer row.
     */
    static JPAProductEntity toEditedEntity(JPAProductEntity managed, Product product) {
        return managed.editedTo(
                product.name(),
                product.description(),
                product.category(),
                product.price().amount(),
                product.stock(),
                product.weight().kilograms(),
                Math.toIntExact(product.version()));
    }

    static Product toDomain(JPAProductEntity entity) {
        return new Product(
                new SKU(entity.getSku()),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                new Money(entity.getPrice()),
                entity.getStock(),
                new Weight(entity.getWeightKg()),
                entity.getVersion());
    }
}
