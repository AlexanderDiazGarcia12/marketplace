package com.ecommerce.marketplace.application.service;

import com.ecommerce.marketplace.application.text.CurrencyText;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import com.ecommerce.marketplace.domain.model.product.Weight;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import io.vavr.control.Validation;

/**
 * Validates one raw CSV data row into a {@link Product}, accumulating every field problem instead of
 * short-circuiting on the first — the same Vavr applicative strategy the web
 * {@code CreateProductCommandFactory} uses, so a row with several bad fields is rejected with all its
 * reasons at once. Each value-object factory returns {@code Either<Failure, T>}; {@code .toValidation()}
 * lifts it so {@link Validation#combine} gathers all {@link Failure}s into a single {@code Seq}.
 *
 * <p>The final type is {@code Validation<Seq<String>, Product>} (legible strings, not {@code Failure}):
 * {@code import_job_errors.error_reason} is a JSONB array of user-facing messages the US-18 status
 * view renders directly, so the accumulated {@code Seq<Failure>} is mapped to messages here rather
 * than leaking failure types across the layer. A valid row yields a {@link Product} with
 * {@code version = 0}; the actual write is an idempotent upsert that owns version accounting.</p>
 *
 * <p>Plain Java (no Spring), consistent with the rest of {@code application}: it is invoked by the
 * US-17 consumer per row and shares the {@code $}/thousands-separator cleaning with the web forms via
 * {@link CurrencyText}.</p>
 */
public final class CsvProductRowValidator {

    private static final long NEW_PRODUCT_VERSION = 0L;

    public Validation<Seq<String>, Product> validate(RawProductRow row) {
        return Validation.combine(
                        validateSku(row.sku()),
                        validateName(row.name()),
                        validateCategory(row.category()),
                        validatePrice(row.price()),
                        validateStock(row.stock()),
                        validateWeight(row.weightKg()))
                .ap((sku, name, category, price, stock, weight) ->
                        new Product(sku, name, description(row.description()), category, price, stock, weight, NEW_PRODUCT_VERSION))
                .mapError(this::toMessages);
    }

    private Validation<Failure, SKU> validateSku(String raw) {
        return SKU.of(raw).toValidation();
    }

    private Validation<Failure, String> validateName(String raw) {
        return Option.of(raw)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toValidation(() -> new Failure.InvalidProductName(raw));
    }

    private Validation<Failure, Category> validateCategory(String raw) {
        return Category.of(raw).toValidation();
    }

    private Validation<Failure, Money> validatePrice(String raw) {
        return Money.of(CurrencyText.strip(raw))
                .flatMap(this::requireStrictlyPositive)
                .toValidation();
    }

    private Either<Failure, Money> requireStrictlyPositive(Money price) {
        return price.amount().signum() > 0
                ? Either.right(price)
                : Either.left(new Failure.InvalidProductPrice(price));
    }

    private Validation<Failure, Integer> validateStock(String raw) {
        return Option.of(raw)
                .map(String::trim)
                .flatMap(value -> Try.of(() -> Integer.parseInt(value)).toOption())
                .filter(value -> value >= 0)
                .toValidation(() -> new Failure.InvalidStock(-1));
    }

    private Validation<Failure, Weight> validateWeight(String raw) {
        return Weight.of(raw).toValidation();
    }

    private String description(String raw) {
        return Option.of(raw).map(String::trim).getOrElse("");
    }

    private Seq<String> toMessages(Seq<Failure> failures) {
        return failures.map(CsvProductRowValidator::message);
    }

    private static String message(Failure failure) {
        return switch (failure) {
            case Failure.InvalidSku ignored -> "SKU must be 3-64 characters: letters, digits or dashes, starting alphanumeric.";
            case Failure.InvalidProductName ignored -> "Name is required.";
            case Failure.InvalidCategory invalid -> "Unknown category '" + invalid.raw() + "'.";
            case Failure.InvalidMoney ignored -> "Price must be a positive amount (e.g. 29.99).";
            case Failure.InvalidProductPrice ignored -> "Price must be a positive amount (e.g. 29.99).";
            case Failure.InvalidStock ignored -> "Stock must be a whole number of 0 or more.";
            case Failure.InvalidWeight ignored -> "Weight must be a non-negative number in kilograms (e.g. 1.250).";
            default -> "The row value is invalid.";
        };
    }
}
