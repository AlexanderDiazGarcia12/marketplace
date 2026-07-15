package com.ecommerce.marketplace.application.ports.out;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import io.vavr.control.Either;

/**
 * Output port for charging a payment. The fake deterministic gateway decides approval/rejection
 * from the {@code PaymentToken} prefix, but that semantics is confined to the adapter:
 * {@link PaymentToken} stays a plain format-validated domain VO, and neither this port nor the
 * domain pattern-matches on token content.
 */
public interface PaymentGatewayPort {

    Either<Failure, PaymentConfirmation> charge(PaymentToken paymentToken, Money amount);
}
