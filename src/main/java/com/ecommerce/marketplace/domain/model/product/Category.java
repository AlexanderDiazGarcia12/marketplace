package com.ecommerce.marketplace.domain.model.product;

import com.ecommerce.marketplace.domain.failure.Failure;
import io.vavr.control.Either;
import io.vavr.control.Option;

import java.util.Arrays;

public enum Category {
    ACCESSORIES("Accessories"),
    BEAUTY("Beauty"),
    BOOKS("Books"),
    CLOTHING("Clothing"),
    ELECTRONICS("Electronics"),
    FOOD_AND_BEVERAGE("Food & Beverage"),
    FOOTWEAR("Footwear"),
    GAMES("Games"),
    GIFTS("Gifts"),
    HEALTH("Health"),
    HOME_AND_OFFICE("Home & Office"),
    KITCHEN("Kitchen"),
    MISC("Misc"),
    OUTDOORS("Outdoors"),
    PETS("Pets"),
    SPORTS("Sports"),
    STATIONERY("Stationery"),
    TOOLS("Tools");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Either<Failure, Category> of(String raw) {
        return Option.of(raw)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .flatMap(Category::parse)
                .toEither(() -> new Failure.InvalidCategory(raw));
    }

    private static Option<Category> parse(String value) {
        return Option.ofOptional(Arrays.stream(values())
                .filter(category -> category.label.equalsIgnoreCase(value))
                .findFirst());
    }
}
