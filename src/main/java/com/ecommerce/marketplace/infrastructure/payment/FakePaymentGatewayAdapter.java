package com.ecommerce.marketplace.infrastructure.payment;

import com.ecommerce.marketplace.application.ports.out.PaymentConfirmation;
import com.ecommerce.marketplace.application.ports.out.PaymentGatewayPort;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import io.vavr.control.Either;

import java.util.Locale;

/**
 * Deterministic fake payment gateway (US-20): the charge outcome is decided purely from a
 * case-insensitive prefix of {@link PaymentToken#value()}, in the spirit of Stripe's test cards.
 * This lets US-22/US-23 checkout tests drive approvals, bank rejections and gateway outages with
 * predictable, reproducible tokens.
 *
 * <p><strong>Where the prefix semantics live.</strong> The mapping "prefix → outcome" is confined
 * to this adapter and nowhere else. {@link PaymentToken} stays a plain format-validated domain VO
 * (US-02) with zero knowledge of prefixes, and {@link PaymentGatewayPort} exposes only the neutral
 * {@code Either<Failure, PaymentConfirmation>} contract. The application and domain layers never
 * pattern-match on token content.</p>
 *
 * <p><strong>Prefix convention</strong> (matched case-insensitively):</p>
 * <ul>
 *   <li>{@code approved-} → the charge succeeds with a {@link PaymentConfirmation}.</li>
 *   <li>{@code insufficient-funds-} → business rejection by the (simulated) issuing bank:
 *       {@code Left(PaymentRejected)} whose reason marks it as a declined charge.</li>
 *   <li>{@code gateway-error-} → simulated infrastructure outage of the gateway itself:
 *       {@code Left(PaymentRejected)} whose reason marks it as a gateway failure, textually
 *       distinct from a bank decline so a caller/log can tell the two apart.</li>
 *   <li>any other token → <em>default rejection</em>. An unrecognized token must never silently
 *       succeed and move money, so the fail-safe default is to decline, not to approve.</li>
 * </ul>
 *
 * <p><strong>Determinism.</strong> The same {@code (PaymentToken, Money)} pair always yields the
 * same result, including a stable {@code confirmationReference} derived from the token and amount
 * (not a random UUID), so downstream checkout tests can assert the reference exactly.</p>
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
            return Either.left(new Failure.PaymentRejected(
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
