package com.ecommerce.marketplace.application.text;

/**
 * Normalises a raw price string from human/spreadsheet input into the plain decimal literal
 * {@link com.ecommerce.marketplace.domain.model.order.Money#of(String)} expects, dropping the
 * currency symbol and thousands separators (e.g. {@code "$29.99"} or {@code "$1,299.00"}). Shared by
 * the web form factories and the CSV row validator so the cleaning is defined once.
 */
public final class CurrencyText {

    private CurrencyText() {
    }

    public static String strip(String raw) {
        return raw == null ? null : raw.trim().replace("$", "").replace(",", "");
    }
}
