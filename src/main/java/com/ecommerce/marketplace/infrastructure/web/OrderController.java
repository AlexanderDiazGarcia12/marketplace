package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.GetOrderUseCase;
import com.ecommerce.marketplace.application.ports.in.ListOrdersUseCase;
import com.ecommerce.marketplace.application.ports.in.query.ListOrdersQuery;
import com.ecommerce.marketplace.application.ports.in.query.OrderDetail;
import com.ecommerce.marketplace.application.ports.out.OrderSummary;
import com.ecommerce.marketplace.application.ports.query.Page;
import com.ecommerce.marketplace.application.ports.query.PageRequest;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.order.OrderStatus;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;

/**
 * Admin order pages. {@code GET /orders} renders a paginated, status-filterable listing of every
 * order (there is no customer identity in this system, so it is intentionally global — the same
 * shape as the product dashboard); {@code GET /orders/{id}} renders one order with its lines.
 *
 * <p>Both stay inside Vavr's {@code Either}, mirroring {@code ProductDashboardController} and
 * {@code ProductDetailController}: an unparseable {@code status} degrades to "no filter" rather than
 * an error, a listing {@code Failure} degrades to an empty view, and two paths lead to the same
 * friendly 404 for the detail — a well-formed id matching no order ({@code Failure.OrderNotFound})
 * and a URL segment that is not a valid UUID ({@code Failure.InvalidOrderId}). The domain aggregate
 * never reaches the templates; {@link OrderRowView}/{@link OrderDetailView} project each shape.</p>
 */
@Controller
public class OrderController {

    private static final int PAGE_SIZE = 20;
    private static final String LIST_VIEW = "orders";
    private static final String DETAIL_VIEW = "order-detail";
    private static final String NOT_FOUND_VIEW = "order-not-found";

    private final ListOrdersUseCase listOrders;
    private final GetOrderUseCase getOrder;

    public OrderController(ListOrdersUseCase listOrders, GetOrderUseCase getOrder) {
        this.listOrders = listOrders;
        this.getOrder = getOrder;
    }

    @GetMapping("/orders")
    public String list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            Model model) {

        Option<OrderStatus> statusFilter = parseStatus(status);
        ListOrdersQuery query = new ListOrdersQuery(statusFilter, PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        return listOrders.list(query)
                .fold(
                        failure -> renderEmpty(statusFilter, model),
                        result -> renderResults(result, statusFilter, model));
    }

    @GetMapping("/orders/{id}")
    public String detail(@PathVariable("id") String rawId, Model model, HttpServletResponse response) {
        return OrderId.of(rawId)
                .flatMap(getOrder::getById)
                .fold(
                        failure -> renderNotFound(rawId, model, response),
                        detail -> renderDetail(detail, model));
    }

    private Option<OrderStatus> parseStatus(String raw) {
        return Option.of(raw)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .flatMap(value -> Try.of(() -> OrderStatus.valueOf(value.toUpperCase(Locale.ROOT))).toOption());
    }

    private String renderResults(Page<OrderSummary> result, Option<OrderStatus> statusFilter, Model model) {
        model.addAttribute("orders", result.content().map(OrderRowView::from).asJava());
        model.addAttribute("isEmpty", result.content().isEmpty());
        model.addAttribute("pagination", PaginationView.from(result));
        populateFilters(statusFilter, model);
        return LIST_VIEW;
    }

    private String renderEmpty(Option<OrderStatus> statusFilter, Model model) {
        model.addAttribute("orders", List.<OrderRowView>empty().asJava());
        model.addAttribute("isEmpty", true);
        model.addAttribute("pagination", PaginationView.empty());
        populateFilters(statusFilter, model);
        return LIST_VIEW;
    }

    private void populateFilters(Option<OrderStatus> statusFilter, Model model) {
        model.addAttribute("selectedStatus", statusFilter.map(OrderStatus::name).getOrElse(""));
        model.addAttribute("statuses", OrderStatus.values());
    }

    private String renderDetail(OrderDetail detail, Model model) {
        model.addAttribute("order", OrderDetailView.from(detail));
        return DETAIL_VIEW;
    }

    private String renderNotFound(String rawId, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("orderId", rawId);
        return NOT_FOUND_VIEW;
    }

    /**
     * View-only pagination state derived from the application-layer {@link Page}. Exposes 1-based
     * display numbers and the previous/next page indices for the controls, so the template stays
     * free of arithmetic — the same shape as {@code ProductDashboardController}'s pagination view,
     * generalized over any {@code Page<?>}.
     */
    record PaginationView(
            int currentPage,
            int totalPages,
            long totalElements,
            boolean hasPrevious,
            boolean hasNext,
            int previousPage,
            int nextPage) {

        static PaginationView from(Page<?> result) {
            int totalPages = Math.max(result.totalPages(), 1);
            boolean hasPrevious = result.page() > 0;
            return new PaginationView(
                    result.page() + 1,
                    totalPages,
                    result.totalElements(),
                    hasPrevious,
                    result.hasNext(),
                    hasPrevious ? result.page() - 1 : 0,
                    result.hasNext() ? result.page() + 1 : result.page());
        }

        static PaginationView empty() {
            return new PaginationView(1, 1, 0, false, false, 0, 0);
        }
    }
}
