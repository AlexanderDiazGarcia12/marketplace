package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.command.PurchaseCommand;
import com.ecommerce.marketplace.application.service.PurchaseProductService;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.Order;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.control.Either;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Minimal transactional checkout endpoint (US-22). {@code GET /checkout} renders a bare form;
 * {@code POST /checkout} runs the purchase Unit of Work and renders a plain result. The polished
 * checkout experience (idempotency key carried across resubmits, per-failure screens, a rich
 * confirmation page) is US-23's scope, not this story — this controller exists so the Unit of Work
 * is demonstrable end-to-end against real Postgres.
 *
 * <p><strong>The transaction boundary lives here, not in the application service.</strong> The
 * application layer is Spring-free (no {@code TransactionTemplate}, forbidden by the ArchUnit gate),
 * so the atomic checkout is opened here around {@link PurchaseProductService#purchase}, the same
 * mechanism {@code ImportProductsController} uses. Because {@code purchase} returns an
 * {@link Either} instead of throwing, a {@code Left} result does not auto-roll-back a
 * {@code TransactionTemplate.execute} block (only a thrown exception does): the controller must
 * explicitly {@code setRollbackOnly()} for <em>every</em> {@code Left}, so a declined payment, a
 * stock conflict, an insufficient-stock or a duplicate-request all roll the main transaction back
 * (stock restored automatically for the ones that already decremented it).</p>
 *
 * <p><strong>Two-phase split for a declined payment.</strong> On {@link Failure.PaymentRejected}
 * only, a second, genuinely independent {@code REQUIRES_NEW} transaction records a {@code REJECTED}
 * order and completes the idempotency key with a rejected snapshot — so a retry with the same key is
 * told "already declined" instead of re-charging. This is why the concrete
 * {@link PurchaseProductService} is injected (its {@code recordRejection} is an
 * infrastructure-orchestration detail, not on the port). {@code recordRejectionIfDeclined} mirrors
 * {@code purchaseAtomically}'s {@code peekLeft(setRollbackOnly)}: a {@code Left} from
 * {@code recordRejection} (e.g. a vanished product) must not commit a half-written compensating
 * transaction either. Other failures deliberately get no phase-2 write and may leave the key
 * {@code IN_PROGRESS} — the documented, accepted residual risk (a later TTL cleanup reclaims those),
 * not a bug to fix here.</p>
 */
@Controller
public class CheckoutController {

    private static final String FORM_VIEW = "checkout";
    private static final String RESULT_VIEW = "checkout-result";

    private final PurchaseProductService purchaseService;
    private final TransactionTemplate mainTransaction;
    private final TransactionTemplate rejectionTransaction;

    public CheckoutController(
            PurchaseProductService purchaseService,
            @Qualifier("marketplaceTransactionTemplate") TransactionTemplate mainTransaction,
            @Qualifier("rejectionTransactionTemplate") TransactionTemplate rejectionTransaction) {
        this.purchaseService = purchaseService;
        this.mainTransaction = mainTransaction;
        this.rejectionTransaction = rejectionTransaction;
    }

    @GetMapping("/checkout")
    public String checkoutForm(Model model) {
        model.addAttribute("suggestedIdempotencyKey", UUID.randomUUID().toString());
        return FORM_VIEW;
    }

    @PostMapping("/checkout")
    public String checkout(
            @RequestParam("sku") String sku,
            @RequestParam("quantity") int quantity,
            @RequestParam("paymentToken") String paymentToken,
            @RequestParam("idempotencyKey") String idempotencyKey,
            Model model,
            HttpServletResponse response) {
        Either<Failure, PurchaseCommand> command = buildCommand(sku, quantity, paymentToken, idempotencyKey);
        Either<Failure, Order> result = command.flatMap(this::purchaseAtomically);
        result.peekLeft(failure -> recordRejectionIfDeclined(command, failure));
        return result.fold(
                failure -> renderRejected(failure, model, response),
                order -> renderConfirmed(order, model, response));
    }

    private Either<Failure, PurchaseCommand> buildCommand(String sku, int quantity, String paymentToken, String idempotencyKey) {
        if (quantity <= 0) {
            return Either.left(new Failure.InvalidOrderQuantity(quantity));
        }
        return SKU.of(sku)
                .flatMap(parsedSku -> PaymentToken.of(paymentToken)
                        .flatMap(parsedToken -> IdempotencyKey.of(idempotencyKey)
                                .map(parsedKey -> new PurchaseCommand(parsedSku, quantity, parsedToken, parsedKey))));
    }

    private Either<Failure, Order> purchaseAtomically(PurchaseCommand command) {
        return mainTransaction.execute(status -> {
            Either<Failure, Order> outcome = purchaseService.purchase(command);
            outcome.peekLeft(failure -> status.setRollbackOnly());
            return outcome;
        });
    }

    private void recordRejectionIfDeclined(Either<Failure, PurchaseCommand> command, Failure failure) {
        if (failure instanceof Failure.PaymentRejected) {
            command.forEach(declined -> rejectionTransaction.execute(status -> {
                Either<Failure, Order> outcome = purchaseService.recordRejection(declined);
                outcome.peekLeft(ignored -> status.setRollbackOnly());
                return outcome;
            }));
        }
    }

    private String renderConfirmed(Order order, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.OK.value());
        model.addAttribute("outcome", "CONFIRMED");
        model.addAttribute("orderId", order.id().value().toString());
        model.addAttribute("sku", order.items().head().sku().value());
        model.addAttribute("quantity", order.items().head().quantity());
        model.addAttribute("total", order.totalAmount().amount().toPlainString());
        return RESULT_VIEW;
    }

    private String renderRejected(Failure failure, Model model, HttpServletResponse response) {
        response.setStatus(statusFor(failure).value());
        model.addAttribute("outcome", "FAILED");
        model.addAttribute("message", messageFor(failure));
        return RESULT_VIEW;
    }

    private static HttpStatus statusFor(Failure failure) {
        return switch (failure) {
            case Failure.PaymentRejected ignored -> HttpStatus.PAYMENT_REQUIRED;
            case Failure.InsufficientStock ignored -> HttpStatus.CONFLICT;
            case Failure.ConcurrentStockConflict ignored -> HttpStatus.CONFLICT;
            case Failure.DuplicateOrderRequest ignored -> HttpStatus.CONFLICT;
            case Failure.ProductNotFound ignored -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static String messageFor(Failure failure) {
        return switch (failure) {
            case Failure.PaymentRejected rejected -> rejected.reason();
            case Failure.InsufficientStock stock ->
                    "Only " + stock.available() + " unit(s) of " + stock.sku().value() + " are available.";
            case Failure.ConcurrentStockConflict conflict ->
                    "The stock for " + conflict.sku().value() + " is changing too fast right now. Please try again.";
            case Failure.DuplicateOrderRequest ignored -> "This purchase is already being processed.";
            case Failure.IdempotencyKeyMismatch ignored ->
                    "This idempotency key was already used for a different purchase.";
            case Failure.ProductNotFound notFound -> "No product found for SKU " + notFound.sku().value() + ".";
            case Failure.InvalidSku ignored -> "The SKU is not valid.";
            case Failure.InvalidPaymentToken ignored -> "The payment token is not valid.";
            case Failure.InvalidIdempotencyKey ignored -> "The idempotency key is not valid.";
            case Failure.InvalidOrderQuantity ignored -> "The quantity must be a positive number.";
            default -> "The purchase could not be completed. Please try again.";
        };
    }
}
