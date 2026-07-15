package com.ecommerce.marketplace.infrastructure.web;

import com.ecommerce.marketplace.application.ports.in.command.PurchaseCommand;
import com.ecommerce.marketplace.application.service.PurchaseProductService;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.Order;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Validation;
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
 * Transactional checkout endpoint. {@code GET /checkout} renders the purchase form (always minting a
 * fresh idempotency key); {@code POST /checkout} runs the purchase and renders a per-failure result
 * or a confirmation.
 *
 * <p>An input-validation failure happens before the purchase runs, so the idempotency store is never
 * touched and the form is re-rendered carrying the same key (fixing a typo stays one attempt). Any
 * failure after the purchase began has already committed the key, so retries route back through
 * {@code GET /checkout} for a fresh one.</p>
 *
 * <p>The transaction boundary lives here because the application layer is Spring-free. Since
 * {@code purchase} returns an {@link Either} rather than throwing, the controller must explicitly
 * {@code setRollbackOnly()} on every {@code Left} to roll back (and restore) stock.</p>
 *
 * <p>A declined payment triggers a second, independent {@code REQUIRES_NEW} transaction that records
 * a {@code REJECTED} order and completes the key with a rejected snapshot, so a retry is told
 * "already declined" instead of re-charging — hence the concrete {@link PurchaseProductService} is
 * injected for its {@code recordRejection}. Other failures get no phase-2 write.</p>
 */
@Controller
public class CheckoutController {

    private static final String FORM_VIEW = "checkout";
    private static final String RESULT_VIEW = "checkout-result";
    private static final String DEFAULT_PAYMENT_TOKEN = "approved-card";

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
    public String checkoutForm(
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "quantity", required = false) Integer quantity,
            @RequestParam(value = "paymentToken", required = false) String paymentToken,
            Model model) {
        model.addAttribute("sku", sku);
        model.addAttribute("quantity", quantity != null ? quantity : 1);
        model.addAttribute("paymentToken", paymentToken != null ? paymentToken : DEFAULT_PAYMENT_TOKEN);
        model.addAttribute("idempotencyKey", UUID.randomUUID().toString());
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
        Validation<Seq<String>, PurchaseCommand> validation = validateForm(sku, quantity, paymentToken, idempotencyKey);
        if (validation.isInvalid()) {
            return renderFormWithErrors(validation.getError(), sku, quantity, paymentToken, idempotencyKey, model, response);
        }
        PurchaseCommand command = validation.get();
        Either<Failure, Order> result = purchaseAtomically(command);
        result.peekLeft(failure -> recordRejectionIfDeclined(command, failure));
        return result.fold(
                failure -> renderRejected(failure, sku, quantity, paymentToken, model, response),
                order -> renderConfirmed(order, model, response));
    }

    private Validation<Seq<String>, PurchaseCommand> validateForm(
            String sku, int quantity, String paymentToken, String idempotencyKey) {
        Validation<String, SKU> validSku = Validation.fromEither(SKU.of(sku).mapLeft(CheckoutController::messageFor));
        Validation<String, Integer> validQuantity = quantity > 0
                ? Validation.valid(quantity)
                : Validation.invalid(messageFor(new Failure.InvalidOrderQuantity(quantity)));
        Validation<String, PaymentToken> validToken =
                Validation.fromEither(PaymentToken.of(paymentToken).mapLeft(CheckoutController::messageFor));
        Validation<String, IdempotencyKey> validKey =
                Validation.fromEither(IdempotencyKey.of(idempotencyKey).mapLeft(CheckoutController::messageFor));
        return Validation.combine(validSku, validQuantity, validToken, validKey)
                .ap(PurchaseCommand::new);
    }

    private String renderFormWithErrors(
            Seq<String> errors, String sku, int quantity, String paymentToken, String idempotencyKey,
            Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        model.addAttribute("errors", errors.asJava());
        model.addAttribute("sku", sku);
        model.addAttribute("quantity", quantity);
        model.addAttribute("paymentToken", paymentToken);
        model.addAttribute("idempotencyKey", idempotencyKey);
        return FORM_VIEW;
    }

    private Either<Failure, Order> purchaseAtomically(PurchaseCommand command) {
        return mainTransaction.execute(status -> {
            Either<Failure, Order> outcome = purchaseService.purchase(command);
            outcome.peekLeft(failure -> status.setRollbackOnly());
            return outcome;
        });
    }

    private void recordRejectionIfDeclined(PurchaseCommand command, Failure failure) {
        if (failure instanceof Failure.PaymentRejected) {
            rejectionTransaction.execute(status -> {
                Either<Failure, Order> outcome = purchaseService.recordRejection(command);
                outcome.peekLeft(ignored -> status.setRollbackOnly());
                return outcome;
            });
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

    private String renderRejected(
            Failure failure, String sku, int quantity, String paymentToken,
            Model model, HttpServletResponse response) {
        response.setStatus(statusFor(failure).value());
        model.addAttribute("outcome", "FAILED");
        model.addAttribute("failureKind", kindFor(failure));
        model.addAttribute("message", messageFor(failure));
        model.addAttribute("retrySku", sku);
        model.addAttribute("retryQuantity", quantity);
        model.addAttribute("retryPaymentToken", paymentToken);
        if (failure instanceof Failure.InsufficientStock stock) {
            model.addAttribute("requested", stock.requested());
            model.addAttribute("available", stock.available());
        }
        return RESULT_VIEW;
    }

    private static String kindFor(Failure failure) {
        return switch (failure) {
            case Failure.InsufficientStock ignored -> "INSUFFICIENT_STOCK";
            case Failure.PaymentRejected ignored -> "PAYMENT_REJECTED";
            case Failure.PaymentGatewayUnavailable ignored -> "GATEWAY_UNAVAILABLE";
            case Failure.ConcurrentStockConflict ignored -> "CONCURRENT_CONFLICT";
            case Failure.DuplicateOrderRequest ignored -> "DUPLICATE";
            case Failure.IdempotencyKeyMismatch ignored -> "KEY_MISMATCH";
            default -> "GENERIC";
        };
    }

    private static HttpStatus statusFor(Failure failure) {
        return switch (failure) {
            case Failure.PaymentRejected ignored -> HttpStatus.PAYMENT_REQUIRED;
            case Failure.PaymentGatewayUnavailable ignored -> HttpStatus.SERVICE_UNAVAILABLE;
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
            case Failure.PaymentGatewayUnavailable ignored ->
                    "The payment service is temporarily unavailable. No charge was made — please try again shortly.";
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
