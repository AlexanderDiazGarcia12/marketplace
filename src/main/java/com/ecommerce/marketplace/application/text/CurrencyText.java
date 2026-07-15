package com.ecommerce.marketplace.application.text;

/**
 * Normalises a raw price string coming from human/spreadsheet input into the plain decimal
 * literal {@link com.ecommerce.marketplace.domain.model.order.Money#of(String)} expects: it drops
 * the currency symbol and thousands separators the reference CSV and the product forms both allow
 * (e.g. {@code "$29.99"} or {@code "$1,299.00"}), leaving parsing/validation to {@code Money}.
 *
 * <p>Lives in {@code application.text} — plain Java, no framework — precisely so all three callers
 * can share it across layers without breaking the hexagon: the web create/edit form factories in
 * {@code infrastructure.web} and the CSV row validator in {@code application.service}. It replaces
 * the byte-identical private {@code stripCurrency} that was duplicated in the two web factories once
 * the CSV importer became a third consumer of the same cleaning.</p>
 */
public final class CurrencyText {

    private CurrencyText() {
    }

    public static String strip(String raw) {
        return raw == null ? null : raw.trim().replace("$", "").replace(",", "");
    }
}
