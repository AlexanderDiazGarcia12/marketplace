package com.ecommerce.marketplace.infrastructure.payment;

import com.ecommerce.marketplace.application.ports.out.PaymentConfirmation;
import com.ecommerce.marketplace.application.ports.out.PaymentGatewayPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import io.vavr.control.Either;

import java.util.Locale;

/**
 * Deterministic fake payment gateway whose charge outcome is decided purely from a case-insensitive
 * prefix of {@link PaymentToken#value()}, in the spirit of Stripe's test cards, so checkout tests can
 * drive approvals, bank rejections and gateway outages with reproducible tokens. The same
 * {@code (PaymentToken, Money)} pair always yields the same result, including a stable
 * {@code confirmationReference} derived from token and amount rather than a random UUID.
 *
 * <p>The prefix → outcome mapping is confined to this adapter: {@link PaymentToken} stays a plain
 * format-validated VO and {@link PaymentGatewayPort} exposes only the neutral
 * {@code Either<Failure, PaymentConfirmation>} contract, so application and domain never
 * pattern-match on token content. Prefixes (case-insensitive): {@code approved-} succeeds;
 * {@code insufficient-funds-} is a bank decline ({@code PaymentRejected}); {@code gateway-error-} is
 * a simulated outage ({@code PaymentGatewayUnavailable}, a distinct variant so callers branch on type
 * rather than string-match the reason); any other token defaults to a decline, so an unrecognized
 * token never silently moves money.</p>
 */
public final class FakePaymentGatewayAdapter implements PaymentGatewayPort {

    static final String APPROVED_PREFIX = "approved-";
    static final String INSUFFICIENT_FUNDS_PREFIX = "insufficient-funds-";
    static final String GATEWAY_ERROR_PREFIX = "gateway-error-";

    private static final String CONFIRMATION_PREFIX = "FAKE-";

    @Override
    public Either<Failure, PaymentConfirmation> charge(PaymentToken paymentToken, Money amount) {
        String normalized = paymentToken.value().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(APPROVED_PREFIX)) {
            return Either.right(approve(paymentToken, amount));
        }
        if (normalized.startsWith(INSUFFICIENT_FUNDS_PREFIX)) {
            return Either.left(new Failure.PaymentRejected(
                    "Payment declined by the issuing bank: insufficient funds"));
        }
        if (normalized.startsWith(GATEWAY_ERROR_PREFIX)) {
            return Either.left(new Failure.PaymentGatewayUnavailable(
                    "Payment gateway error: the simulated gateway is unavailable"));
        }
        return Either.left(new Failure.PaymentRejected(
                "Payment declined: unrecognized payment token"));
    }

    private PaymentConfirmation approve(PaymentToken paymentToken, Money amount) {
        return new PaymentConfirmation(paymentToken, amount, confirmationReference(paymentToken, amount));
    }

    private String confirmationReference(PaymentToken paymentToken, Money amount) {
        int fingerprint = (paymentToken.value() + '|' + amount.amount().toPlainString()).hashCode();
        return CONFIRMATION_PREFIX + Integer.toHexString(fingerprint).toUpperCase(Locale.ROOT);
    }
}
