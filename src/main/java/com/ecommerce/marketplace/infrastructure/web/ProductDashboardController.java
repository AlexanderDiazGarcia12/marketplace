package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.SearchProductUseCase;
import com.ecommerce.marketplace.application.ports.in.command.SearchProductsCommand;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.application.ports.query.PageRequest;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.product.Category;
import com.ecommerce.marketplace.domain.model.product.Product;
import io.vavr.control.Either;
import io.vavr.control.Option;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Dashboard / product-listing page. {@code GET /} and {@code GET /products} render paginated catalog
 * results through {@link SearchProductUseCase}. The query parameters {@code q} (free-text),
 * {@code category} and {@code page} are all optional; an unparseable {@code category} degrades to
 * "no filter" and a {@link Failure} degrades to an empty result view rather than a stacktrace. The
 * domain {@code Product} never reaches the template — {@link ProductCardView} projects each row.
 */
@Controller
public class ProductDashboardController {

    private static final int PAGE_SIZE = 12;

    private final SearchProductUseCase searchProducts;

    public ProductDashboardController(SearchProductUseCase searchProducts) {
        this.searchProducts = searchProducts;
    }

    @GetMapping({"/", "/products"})
    public String dashboard(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            Model model) {

        Option<String> searchText = Option.of(query).map(String::trim).filter(value -> !value.isEmpty());
        Option<Category> categoryFilter = Option.of(category)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .flatMap(raw -> Category.of(raw).toOption());

        SearchProductsCommand command = new SearchProductsCommand(
                searchText,
                categoryFilter,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        return searchProducts.search(command)
                .fold(
                        failure -> renderEmpty(searchText, categoryFilter, model),
                        result -> renderResults(result, searchText, categoryFilter, model));
    }

    private String renderResults(Page<Product> result, Option<String> searchText, Option<Category> categoryFilter, Model model) {
        model.addAttribute("products", result.content().map(ProductCardView::from).asJava());
        model.addAttribute("isEmpty", result.content().isEmpty());
        model.addAttribute("pagination", PaginationView.from(result));
        populateFilters(searchText, categoryFilter, model);
        return "dashboard";
    }

    private String renderEmpty(Option<String> searchText, Option<Category> categoryFilter, Model model) {
        model.addAttribute("products", io.vavr.collection.List.<ProductCardView>empty().asJava());
        model.addAttribute("isEmpty", true);
        model.addAttribute("pagination", PaginationView.empty());
        populateFilters(searchText, categoryFilter, model);
        return "dashboard";
    }

    private void populateFilters(Option<String> searchText, Option<Category> categoryFilter, Model model) {
        model.addAttribute("query", searchText.getOrElse(""));
        model.addAttribute("selectedCategory", categoryFilter.map(Category::label).getOrElse(""));
        model.addAttribute("categories", Category.values());
    }

    /**
     * View-only pagination state derived from the application-layer {@link Page}. Exposes 1-based
     * display numbers ({@code currentPage}/{@code totalPages}) and the previous/next page indices
     * for the controls, so the template stays free of arithmetic.
     */
    record PaginationView(
            int page,
            int currentPage,
            int totalPages,
            long totalElements,
            boolean hasPrevious,
            boolean hasNext,
            int previousPage,
            int nextPage) {

        static PaginationView from(Page<Product> result) {
            int totalPages = Math.max(result.totalPages(), 1);
            boolean hasPrevious = result.page() > 0;
            return new PaginationView(
                    result.page(),
                    result.page() + 1,
                    totalPages,
                    result.totalElements(),
                    hasPrevious,
                    result.hasNext(),
                    hasPrevious ? result.page() - 1 : 0,
                    result.hasNext() ? result.page() + 1 : result.page());
        }

        static PaginationView empty() {
            return new PaginationView(0, 1, 1, 0, false, false, 0, 0);
        }
    }
}
