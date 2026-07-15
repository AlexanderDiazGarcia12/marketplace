package com.ecommerce.marketplace.domain.failure;

import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.collection.Seq;

/**
 * Sealed hierarchy of every business failure that can occur in the marketplace domain.
 *
 * <p>Failures are modeled as data, not exceptions: services return {@code Either<Failure, T>}
 * instead of throwing, and callers exhaustively pattern-match over this sealed interface. The
 * whole hierarchy is plain Java + Vavr, with zero framework dependencies — errors are values,
 * so there is intentionally no {@code domain.exception} package.</p>
 */
public sealed interface Failure {

    // ---------------------------------------------------------------------
    // Value-object validation failures (raised while parsing raw input into
    // domain value objects, e.g. Category.of, SKU.of, Money.of, ...).
    // ---------------------------------------------------------------------

    /** The raw category label does not match any known {@code Category}. */
    record InvalidCategory(String raw) implements Failure {
    }

    /** The raw value does not match the required SKU format. */
    record InvalidSku(String raw) implements Failure {
    }

    /** The raw weight (in kilograms) is missing, negative or unparsable; {@code raw} is the input in textual form. */
    record InvalidWeight(String raw) implements Failure {
    }

    /** The raw monetary amount is missing, negative or unparsable; {@code raw} is the input in textual form. */
    record InvalidMoney(String raw) implements Failure {
    }

    /** The raw order id is missing or not a valid UUID; {@code raw} is the input in textual form. */
    record InvalidOrderId(String raw) implements Failure {
    }

    /** The raw idempotency key is missing, blank or exceeds the maximum length. */
    record InvalidIdempotencyKey(String raw) implements Failure {
    }

    /** The raw payment token is missing, blank or exceeds the maximum length. */
    record InvalidPaymentToken(String raw) implements Failure {
    }

    /** The requested stock quantity is not a valid non-negative/positive value. */
    record InvalidStock(int quantity) implements Failure {
    }

    /** The product name is missing or blank. */
    record InvalidProductName(String name) implements Failure {
    }

    /** A product was built with a price that is not strictly positive (the {@code products} table requires {@code price > 0}). */
    record InvalidProductPrice(Money price) implements Failure {
    }

    /** The order line quantity is not strictly positive. */
    record InvalidOrderQuantity(int quantity) implements Failure {
    }

    /** An order was built with no line items. */
    record EmptyOrder(OrderId orderId) implements Failure {
    }

    // ---------------------------------------------------------------------
    // Catalog / inventory failures.
    // ---------------------------------------------------------------------

    /** No product exists for the given SKU. */
    record ProductNotFound(SKU sku) implements Failure {
    }

    /** No order exists for the given id. */
    record OrderNotFound(OrderId orderId) implements Failure {
    }

    /** There is not enough stock available to satisfy the requested quantity. */
    record InsufficientStock(SKU sku, int requested, int available) implements Failure {
    }

    /** A product with this SKU already exists in the catalog. */
    record DuplicateSku(SKU sku) implements Failure {
    }

    /** Optimistic-locking conflict: the stock for this SKU was concurrently modified. */
    record ConcurrentStockConflict(SKU sku) implements Failure {
    }

    /** A row of the product-import CSV failed validation. */
    record InvalidCsvRow(int row, Seq<String> reasons) implements Failure {
    }

    /**
     * The uploaded CSV was rejected at the envelope level before any job was created: wrong file
     * type/extension, missing or mismatched header, empty file, or oversized. Distinct from
     * {@link InvalidCsvRow}, which reports a single malformed data row during async processing.
     * {@code reason} is a short, user-facing diagnostic the upload form renders inline.
     */
    record InvalidCsvUpload(String reason) implements Failure {
    }

    /**
     * No import job exists for the given id, or the id in the status URL is not a valid UUID.
     * {@code jobId} is the raw identifier as it appeared in the request, in textual form.
     */
    record ImportJobNotFound(String jobId) implements Failure {
    }

    // ---------------------------------------------------------------------
    // Checkout / payment failures.
    // ---------------------------------------------------------------------

    /** The payment gateway rejected the payment attempt. */
    record PaymentRejected(String reason) implements Failure {
    }

    /**
     * The payment gateway itself is unavailable — a transient infrastructure outage, not a
     * customer-facing decline. Unlike {@link PaymentRejected} (a permanent decision for that
     * attempt), the charge was neither approved nor refused, so a retry once the gateway
     * recovers is legitimate.
     */
    record PaymentGatewayUnavailable(String reason) implements Failure {
    }

    /** A request with this idempotency key is already being processed, or was replayed while in flight. */
    record DuplicateOrderRequest(IdempotencyKey idempotencyKey) implements Failure {
    }

    /**
     * A request reused an existing idempotency key but its payload hash does not match the
     * original request stored for that key (RFC-style idempotency-key replay with a different body).
     */
    record IdempotencyKeyMismatch(IdempotencyKey idempotencyKey) implements Failure {
    }

    // ---------------------------------------------------------------------
    // Messaging / outbox failures.
    // ---------------------------------------------------------------------

    /**
     * A domain event could not be prepared for the transactional outbox (e.g. the payload could
     * not be serialized, or its type has no Kafka topic mapping). A genuine failure of the
     * {@code outbox_events} INSERT surfaces as a persistence exception on flush, not as this
     * variant, which covers rejections decided before the insert is attempted. {@code eventType}
     * identifies the event; {@code reason} is a short diagnostic.
     */
    record EventPublishFailed(String eventType, String reason) implements Failure {
    }
}
