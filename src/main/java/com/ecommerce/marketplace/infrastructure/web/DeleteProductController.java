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
 * Soft-delete action. {@code POST /products/{sku}/delete} logically removes a product and redirects
 * back to the dashboard with a success flash (post-redirect-get avoids a re-submitted delete on
 * refresh). An invalid SKU and a SKU matching no live product — including one already deleted — both
 * fold into the same friendly 404 view, never a stacktrace and never a double delete.
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
