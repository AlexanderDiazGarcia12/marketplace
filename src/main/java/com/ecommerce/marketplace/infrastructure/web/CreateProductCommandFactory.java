package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.command.CreateProductCommand;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.SKU;
import com.ecommerce.marketplace.domain.model.product.Weight;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import io.vavr.control.Try;
import io.vavr.control.Validation;

/**
 * Turns a raw {@link ProductForm} into a validated {@link CreateProductCommand}, accumulating
 * every field failure with Vavr {@link Validation} instead of short-circuiting on the first one.
 *
 * <p>Each value-object factory returns {@code Either<Failure, T>}; {@code .toValidation()} lifts
 * it into the applicative world so {@link Validation#combine} gathers all {@link Failure}s into a
 * single {@code Seq} — a form with several bad fields reports them together in one round trip.</p>
 */
final class CreateProductCommandFactory {

    private CreateProductCommandFactory() {
    }

    static Validation<Seq<Failure>, CreateProductCommand> from(ProductForm form) {
        return Validation.combine(
                        validateSku(form.getSku()),
                        validateName(form.getName()),
                        validateCategory(form.getCategory()),
                        validatePrice(form.getPrice()),
                        validateStock(form.getStock()),
                        validateWeight(form.getWeightKg()))
                .ap((sku, name, category, price, stock, weight) ->
                        new CreateProductCommand(sku, name, description(form.getDescription()), category, price, stock, weight));
    }

    private static Validation<Failure, SKU> validateSku(String raw) {
        return SKU.of(raw).toValidation();
    }

    private static Validation<Failure, String> validateName(String raw) {
        return Option.of(raw)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toValidation(() -> new Failure.InvalidProductName(raw));
    }

    private static Validation<Failure, Category> validateCategory(String raw) {
        return Category.of(raw).toValidation();
    }

    private static Validation<Failure, Money> validatePrice(String raw) {
        return Money.of(stripCurrency(raw)).toValidation();
    }

    private static Validation<Failure, Integer> validateStock(String raw) {
        return Option.of(raw)
                .map(String::trim)
                .flatMap(value -> Try.of(() -> Integer.parseInt(value)).toOption())
                .filter(value -> value >= 0)
                .toValidation(() -> new Failure.InvalidStock(-1));
    }

    private static Validation<Failure, Weight> validateWeight(String raw) {
        return Weight.of(raw).toValidation();
    }

    private static String description(String raw) {
        return Option.of(raw).map(String::trim).getOrElse("");
    }

    private static String stripCurrency(String raw) {
        return raw == null ? null : raw.trim().replace("$", "").replace(",", "");
    }
}
