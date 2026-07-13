package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.collection.Seq;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * View-facing projection of accumulated {@link Failure}s: a per-field message map the template
 * reads with {@code th:if} / {@code th:text} to render inline errors, plus an ordered list of
 * form-level messages (e.g. a duplicate SKU rejected by persistence). Presentation-only — it
 * never leaks a {@code Failure} type into the template.
 */
public record ProductFormErrors(Map<String, String> fieldErrors, Seq<String> formErrors) {

    private static final String GENERAL_FIELD = "__general__";

    static ProductFormErrors of(Seq<Failure> failures) {
        Map<String, String> byField = new LinkedHashMap<>();
        io.vavr.collection.List<String> formLevel = io.vavr.collection.List.empty();
        for (Failure failure : failures) {
            FieldMessage translated = translate(failure);
            if (GENERAL_FIELD.equals(translated.field())) {
                formLevel = formLevel.append(translated.message());
            } else {
                byField.putIfAbsent(translated.field(), translated.message());
            }
        }
        return new ProductFormErrors(byField, formLevel);
    }

    public boolean hasErrors() {
        return !fieldErrors.isEmpty() || !formErrors.isEmpty();
    }

    public String field(String name) {
        return fieldErrors.get(name);
    }

    private static FieldMessage translate(Failure failure) {
        return switch (failure) {
            case Failure.InvalidSku ignored ->
                    new FieldMessage("sku", "SKU must be 3-64 characters: letters, digits or dashes, starting alphanumeric.");
            case Failure.InvalidProductName ignored ->
                    new FieldMessage("name", "Name is required.");
            case Failure.InvalidCategory ignored ->
                    new FieldMessage("category", "Choose a valid category.");
            case Failure.InvalidMoney ignored ->
                    new FieldMessage("price", "Price must be a positive amount (e.g. 29.99).");
            case Failure.InvalidProductPrice ignored ->
                    new FieldMessage("price", "Price must be a positive amount (e.g. 29.99).");
            case Failure.InvalidStock ignored ->
                    new FieldMessage("stock", "Stock must be a whole number of 0 or more.");
            case Failure.InvalidWeight ignored ->
                    new FieldMessage("weightKg", "Weight must be a non-negative number in kilograms (e.g. 1.250).");
            case Failure.DuplicateSku duplicate ->
                    new FieldMessage("sku", "A product with SKU '" + duplicate.sku().value() + "' already exists.");
            default -> new FieldMessage(GENERAL_FIELD, "The submitted value is invalid.");
        };
    }

    private record FieldMessage(String field, String message) {
    }
}
