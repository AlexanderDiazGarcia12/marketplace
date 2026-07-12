package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import io.vavr.control.Either;

/**
 * Output port for charging a payment (US-20/US-22).
 *
 * <p>Exact reference signature from the backlog: {@code charge(PaymentToken, Money):
 * Either<Failure, PaymentConfirmation>}. The fake deterministic gateway (US-20) will decide
 * approval/rejection from the {@code PaymentToken} prefix, but that semantics is confined to the
 * adapter — {@link PaymentToken} itself (US-02) stays a plain format-validated domain VO with no
 * knowledge of prefixes; this port and the domain never pattern-match on token content.</p>
 */
public interface PaymentGatewayPort {

    Either<Failure, PaymentConfirmation> charge(PaymentToken paymentToken, Money amount);
}
