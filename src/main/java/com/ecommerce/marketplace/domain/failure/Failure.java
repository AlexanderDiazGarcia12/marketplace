package com.ecommerce.marketplace.domain.failure;

import com.ecommerce.marketplace.domain.model.order.IdempotencyKey;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.OrderId;
import com.ecommerce.marketplace.domain.model.product.SKU;
import io.vavr.collection.Seq;

/**
 * Sealed hierarchy of every business failure that can occur in the marketplace domain.
 *
 * <p>Failures are modeled as data, not exceptions: application and domain services return
 * {@code Either<Failure, T>} instead of throwing, and callers exhaustively pattern-match over
 * this sealed interface (enforced by the compiler via {@code permits} + {@code switch}).</p>
 *
 * <p>This type and all its variants live in {@code domain.failure} and have zero dependencies on
 * Spring, Jakarta, Jackson or Lombok — pure Java 25 + Vavr, consistent with the rest of the
 * domain layer. There is intentionally no {@code domain.exception} package: errors are values.</p>
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

    /**
     * The raw weight (in kilograms) is missing, negative or unparsable.
     * {@code raw} is the input value in its textual form as received, regardless of which
     * {@code Weight.of(...)} overload produced this failure.
     */
    record InvalidWeight(String raw) implements Failure {
    }

    /**
     * The raw monetary amount is missing, negative or unparsable.
     * {@code raw} is the input value in its textual form as received, regardless of which
     * {@code Money.of(...)} overload produced this failure.
     */
    record InvalidMoney(String raw) implements Failure {
    }

    /**
     * The raw order id is missing or not a valid UUID.
     * {@code raw} is the input value in its textual form as received, regardless of which
     * {@code OrderId.of(...)} overload produced this failure.
     */
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
    // Catalog / inventory failures (US-03 mandated minimum set).
    // ---------------------------------------------------------------------

    /** No product exists for the given SKU. */
    record ProductNotFound(SKU sku) implements Failure {
    }

    /**
     * No order exists for the given id — the read counterpart of {@link ProductNotFound} for the
     * admin order-detail view. Carries the parsed {@link OrderId} (as {@link ProductNotFound}
     * carries its {@code SKU}); a raw string that is not a valid UUID never reaches this point,
     * it fails earlier as {@link InvalidOrderId}.
     */
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
     * The uploaded CSV was rejected at the envelope level before any job was created (US-16):
     * wrong file type/extension, missing or mismatched header line, empty file, or a size beyond
     * the accepted limit. This is distinct from {@link InvalidCsvRow}, which reports a single
     * malformed data row during the asynchronous per-row processing (US-17). {@code reason} is a
     * short, user-facing diagnostic the upload form renders inline.
     */
    record InvalidCsvUpload(String reason) implements Failure {
    }

    /**
     * No import job exists for the given id, or the id in the status-view URL is not a valid UUID
     * at all (US-18). {@code jobId} is the raw identifier as it appeared in the request, in textual
     * form — the domain stays free of the application-layer {@code ImportJobId} type, exactly as the
     * value-object validation failures above carry the raw {@code String} rather than the parsed VO.
     */
    record ImportJobNotFound(String jobId) implements Failure {
    }

    // ---------------------------------------------------------------------
    // Checkout / payment failures (US-03 mandated minimum set).
    // ---------------------------------------------------------------------

    /** The payment gateway rejected the payment attempt. */
    record PaymentRejected(String reason) implements Failure {
    }

    /**
     * The payment gateway itself is unavailable — a transient infrastructure outage, not a
     * customer-facing decline. Unlike {@link PaymentRejected} (the issuing bank refused the charge,
     * a permanent business outcome for that attempt), the charge here was neither approved nor
     * refused, so a retry once the gateway recovers is legitimate.
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
    // Messaging / outbox failures (US-15).
    // ---------------------------------------------------------------------

    /**
     * A domain event could not be prepared for the transactional outbox (e.g. the payload could
     * not be serialized, or its event type has no Kafka topic mapping). A genuine failure of the
     * {@code outbox_events} INSERT itself surfaces as a persistence exception on flush, rolling
     * back the caller's ambient transaction, not as this variant — this one covers rejections
     * decided before the insert is even attempted. {@code eventType} identifies the event that
     * failed; {@code reason} is a short diagnostic.
     */
    record EventPublishFailed(String eventType, String reason) implements Failure {
    }
}
