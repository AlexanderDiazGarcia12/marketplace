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
 * Product detail page. {@code GET /products/{sku}} renders a single product's properties. A
 * well-formed SKU matching no live product and a segment that is not a valid SKU both fold into the
 * same friendly 404 view rather than a stacktrace.
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
