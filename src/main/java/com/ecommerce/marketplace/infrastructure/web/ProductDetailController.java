package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.GetProductUseCase;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Product detail page (US-10). {@code GET /products/{sku}} renders a single product's properties.
 *
 * <p>Two paths lead to the same friendly HTTP 404 view (never a stacktrace): a well-formed SKU
 * that matches no live product ({@link Failure.ProductNotFound}), and a URL segment that is not a
 * valid SKU at all ({@link Failure.InvalidSku}). A string that cannot even be a SKU cannot
 * identify a stored product, so treating it as "not found" is the honest, user-facing outcome —
 * the whole flow stays inside Vavr's {@code Either}, so no invalid input ever escapes as an
 * unhandled exception.</p>
 */
@Controller
public class ProductDetailController {

    private static final String DETAIL_VIEW = "product-detail";
    private static final String NOT_FOUND_VIEW = "product-not-found";

    private final GetProductUseCase getProduct;

    public ProductDetailController(GetProductUseCase getProduct) {
        this.getProduct = getProduct;
    }

    @GetMapping("/products/{sku}")
    public String detail(@PathVariable("sku") String rawSku, Model model, HttpServletResponse response) {
        return SKU.of(rawSku)
                .flatMap(getProduct::getBySku)
                .fold(
                        failure -> renderNotFound(rawSku, model, response),
                        product -> renderDetail(product, model));
    }

    private String renderDetail(Product product, Model model) {
        model.addAttribute("product", ProductDetailView.from(product));
        return DETAIL_VIEW;
    }

    private String renderNotFound(String rawSku, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("sku", rawSku);
        return NOT_FOUND_VIEW;
    }
}
