package com.ecommerce.marketplace.application.ports.in;

import com.ecommerce.marketplace.application.ports.in.command.CreateProductCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;

/**
 * Input port: register a new product in the catalog (US-09).
 *
 * <p>Fails with {@link Failure.DuplicateSku} when the SKU already exists — the use case never
 * throws; every business outcome, success or failure, is a value.</p>
 */
public interface CreateProductUseCase {

    Either<Failure, Product> createProduct(CreateProductCommand command);
}
