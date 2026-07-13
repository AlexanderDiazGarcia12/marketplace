package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.DeleteProductUseCase;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.SKU;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Soft-delete action (US-12). {@code POST /products/{sku}/delete} logically removes a product and
 * redirects back to the dashboard with a success flash; the detail view (US-10) exposes the button
 * that submits here behind a JS {@code confirm()} (no dedicated confirmation page for a size-S
 * story).
 *
 * <p>Every outcome stays a value: a URL segment that is not a valid SKU
 * ({@link Failure.InvalidSku}) and a SKU that matches no live product — including one already
 * deleted — ({@link Failure.ProductNotFound}) both fold into the same friendly 404 view, never a
 * stacktrace and never a double delete. Post-redirect-get avoids a re-submitted delete on refresh.
 * The flow stays inside Vavr's {@code Either}; the only place a persistence exception could be
 * caught is the repository adapter, and soft delete needs none.</p>
 */
@Controller
public class DeleteProductController {

    private static final String NOT_FOUND_VIEW = "product-not-found";
    private static final String DASHBOARD_REDIRECT = "redirect:/products";

    private final DeleteProductUseCase deleteProduct;

    public DeleteProductController(DeleteProductUseCase deleteProduct) {
        this.deleteProduct = deleteProduct;
    }

    @PostMapping("/products/{sku}/delete")
    public String delete(
            @PathVariable("sku") String rawSku,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        return SKU.of(rawSku)
                .flatMap(deleteProduct::deleteBySku)
                .fold(
                        failure -> renderNotFound(rawSku, model, response),
                        deleted -> redirectToDashboard(rawSku, redirectAttributes));
    }

    private String redirectToDashboard(String rawSku, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("deletedSku", rawSku);
        return DASHBOARD_REDIRECT;
    }

    private String renderNotFound(String rawSku, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("sku", rawSku);
        return NOT_FOUND_VIEW;
    }
}
