package com.ecommerce.marketplace.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Dashboard / product-listing shell (US-08).
 *
 * <p>{@code application.ports.in.SearchProductUseCase} (US-04) has no implementation yet — wiring
 * a real search adapter is US-13's job. Per the vertical-slicing convention already established by
 * US-05 (see {@code infrastructure.config.SpringDependencyInjectionConfig}), this story does not
 * fabricate a stub bean for that port just to have something to render. Instead this controller
 * renders the listing shell using {@link #SAMPLE_PRODUCTS}, a static, view-only fixture that never
 * touches the port's contract — so US-13 replaces the model population here without touching the
 * template's {@code th:each} contract ({@code name}, {@code sku}, {@code category}, {@code priceLabel},
 * {@code stock}).</p>
 */
@Controller
public class ProductDashboardController {

    private static final List<ProductCardView> SAMPLE_PRODUCTS = List.of(
            new ProductCardView("Running Shoes", "RS-001", "Footwear", "$89.99", 150),
            new ProductCardView("Organic Coffee Beans", "CB-010", "Food & Beverage", "$18.75", 500),
            new ProductCardView("Wireless Mouse", "WM-042", "Electronics", "$29.99", 75),
            new ProductCardView("Leather Wallet", "LW-019", "Accessories", "$39.99", 180),
            new ProductCardView("Camping Tent", "CT-005", "Outdoors", "$199.99", 25),
            new ProductCardView("Protein Powder", "PP-012", "Food & Beverage", "$34.99", 0)
    );

    @GetMapping({"/", "/products"})
    public String dashboard(Model model) {
        model.addAttribute("isEmpty", SAMPLE_PRODUCTS.isEmpty());
        model.addAttribute("products", SAMPLE_PRODUCTS);
        return "dashboard";
    }

    /**
     * View-only fixture row for the dashboard shell. Deliberately distinct from the domain
     * {@code Product} record (which requires a validated {@code SKU}, {@code Money}, etc.) — this
     * is presentation-layer sample data, not a domain object, and is never assembled from or
     * passed through {@code application.ports.in.SearchProductUseCase}.
     */
    record ProductCardView(String name, String sku, String category, String priceLabel, int stock) {
    }
}
