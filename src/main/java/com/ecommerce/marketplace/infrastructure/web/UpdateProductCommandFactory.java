package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.command.UpdateProductCommand;
import com.ecommerce.marketplace.application.text.CurrencyText;
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
 * Turns a raw {@link ProductForm} into a validated {@link UpdateProductCommand}, using Vavr
 * {@link Validation} to accumulate every field failure so an edit with several bad fields reports
 * them together in one round trip.
 *
 * <p>The {@link SKU} is passed in from the already-validated URL path rather than re-parsed. The
 * hidden {@code version} field is parsed into the {@code expectedVersion} the optimistic check runs
 * against; a missing or malformed value is surfaced as {@link Failure.ConcurrentStockConflict} so
 * the view offers a reload-and-retry rather than a generic error.</p>
 */
final class UpdateProductCommandFactory {

    private UpdateProductCommandFactory() {
    }

    static Validation<Seq<Failure>, UpdateProductCommand> from(SKU sku, ProductForm form) {
        return Validation.combine(
                        validateName(form.getName()),
                        validateCategory(form.getCategory()),
                        validatePrice(form.getPrice()),
                        validateStock(form.getStock()),
                        validateWeight(form.getWeightKg()),
                        validateVersion(sku, form.getVersion()))
                .ap((name, category, price, stock, weight, version) ->
                        new UpdateProductCommand(sku, name, description(form.getDescription()), category, price, stock, weight, version));
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
        return Money.of(CurrencyText.strip(raw)).toValidation();
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

    private static Validation<Failure, Long> validateVersion(SKU sku, String raw) {
        return Option.of(raw)
                .map(String::trim)
                .flatMap(value -> Try.of(() -> Long.parseLong(value)).toOption())
                .filter(value -> value >= 0)
                .toValidation(() -> new Failure.ConcurrentStockConflict(sku));
    }

    private static String description(String raw) {
        return Option.of(raw).map(String::trim).getOrElse("");
    }
}
