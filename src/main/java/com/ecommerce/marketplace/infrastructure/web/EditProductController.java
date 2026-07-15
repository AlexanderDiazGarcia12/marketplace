package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.GetProductUseCase;
import com.ecommerce.marketplace.application.ports.in.UpdateProductUseCase;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.Product;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.collection.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;

/**
 * Edit-product form. {@code GET /products/{sku}/edit} renders a form prefilled with current values
 * plus a hidden {@code version} field; {@code POST /products/{sku}/edit} re-validates every field
 * (accumulating failures with Vavr {@code Validation}) and, only if valid, invokes
 * {@link UpdateProductUseCase}. An unknown SKU folds into a 404; a stale-version
 * {@link Failure.ConcurrentStockConflict} re-renders the form with a reload-and-retry banner rather
 * than a silent lost update.
 */
@Controller
public class EditProductController {

    private static final String EDIT_VIEW = "product-edit";
    private static final String NOT_FOUND_VIEW = "product-not-found";
    private static final String DETAIL_REDIRECT = "redirect:/products/";

    private final GetProductUseCase getProduct;
    private final UpdateProductUseCase updateProduct;

    public EditProductController(GetProductUseCase getProduct, UpdateProductUseCase updateProduct) {
        this.getProduct = getProduct;
        this.updateProduct = updateProduct;
    }

    @GetMapping("/products/{sku}/edit")
    public String editForm(@PathVariable("sku") String rawSku, Model model, HttpServletResponse response) {
        return SKU.of(rawSku)
                .flatMap(getProduct::getBySku)
                .fold(
                        failure -> renderNotFound(rawSku, model, response),
                        product -> renderForm(product, model));
    }

    @PostMapping("/products/{sku}/edit")
    public String submit(
            @PathVariable("sku") String rawSku,
            @ModelAttribute("productForm") ProductForm form,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        return SKU.of(rawSku).fold(
                invalidSku -> renderNotFound(rawSku, model, response),
                sku -> submitFor(sku, form, model, redirectAttributes));
    }

    private String submitFor(SKU sku, ProductForm form, Model model, RedirectAttributes redirectAttributes) {
        return UpdateProductCommandFactory.from(sku, form).toEither()
                .flatMap(command -> updateProduct.updateProduct(command).mapLeft(List::of))
                .fold(
                        failures -> renderWithErrors(sku.value(), form, model, ProductFormErrors.of(failures)),
                        product -> redirectToDetail(product, redirectAttributes));
    }

    private String renderForm(Product product, Model model) {
        model.addAttribute("productForm", EditProductForm.from(product));
        model.addAttribute("sku", product.sku().value());
        model.addAttribute("categories", categoryLabels());
        model.addAttribute("errors", ProductFormErrors.of(List.empty()));
        return EDIT_VIEW;
    }

    private String renderWithErrors(String rawSku, ProductForm form, Model model, ProductFormErrors errors) {
        model.addAttribute("productForm", form);
        model.addAttribute("sku", rawSku);
        model.addAttribute("categories", categoryLabels());
        model.addAttribute("errors", errors);
        return EDIT_VIEW;
    }

    private String redirectToDetail(Product product, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("updatedName", product.name());
        return DETAIL_REDIRECT + product.sku().value();
    }

    private String renderNotFound(String rawSku, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("sku", rawSku);
        return NOT_FOUND_VIEW;
    }

    private static java.util.List<String> categoryLabels() {
        return Arrays.stream(Category.values()).map(Category::label).toList();
    }
}
