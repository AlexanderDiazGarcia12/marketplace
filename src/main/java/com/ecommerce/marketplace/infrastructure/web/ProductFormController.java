package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.CreateProductUseCase;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.collection.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;

/**
 * Create-product form. {@code GET /products/new} renders an empty form; {@code POST /products}
 * accumulates all field failures with {@link CreateProductCommandFactory} and, only if fully valid,
 * invokes {@link CreateProductUseCase}. A {@link Failure.DuplicateSku} from persistence is folded
 * into an inline field error rather than a thrown exception.
 */
@Controller
public class ProductFormController {

    private static final String FORM_VIEW = "product-form";
    private static final String DASHBOARD_REDIRECT = "redirect:/products";

    private final CreateProductUseCase createProduct;

    public ProductFormController(CreateProductUseCase createProduct) {
        this.createProduct = createProduct;
    }

    @GetMapping("/products/new")
    public String newProduct(Model model) {
        model.addAttribute("productForm", new ProductForm());
        model.addAttribute("categories", categoryLabels());
        model.addAttribute("errors", ProductFormErrors.of(List.empty()));
        return FORM_VIEW;
    }

    @PostMapping("/products")
    public String create(@ModelAttribute("productForm") ProductForm form, Model model, RedirectAttributes redirectAttributes) {
        return CreateProductCommandFactory.from(form)
                .toEither()
                .flatMap(command -> createProduct.createProduct(command).mapLeft(List::of))
                .fold(
                        failures -> renderWithErrors(form, model, ProductFormErrors.of(failures)),
                        product -> redirectToDashboard(product, redirectAttributes));
    }

    private String renderWithErrors(ProductForm form, Model model, ProductFormErrors errors) {
        model.addAttribute("productForm", form);
        model.addAttribute("categories", categoryLabels());
        model.addAttribute("errors", errors);
        return FORM_VIEW;
    }

    private String redirectToDashboard(Product product, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("createdSku", product.sku().value());
        redirectAttributes.addFlashAttribute("createdName", product.name());
        return DASHBOARD_REDIRECT;
    }

    private static java.util.List<String> categoryLabels() {
        return Arrays.stream(Category.values()).map(Category::label).toList();
    }
}
