package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.CreateProductCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Input port: register a new product in the catalog. Fails with {@link Failure.DuplicateSku} when
 * the SKU already exists; every outcome is a value, never a thrown exception.
 */
public interface CreateProductUseCase {

    Either<Failure, Product> createProduct(CreateProductCommand command);
}
