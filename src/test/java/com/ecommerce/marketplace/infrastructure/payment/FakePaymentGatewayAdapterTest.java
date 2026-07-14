package com.ecommerce.marketplace.infrastructure.payment;

import com.ecommerce.marketplace.application.ports.out.PaymentConfirmation;
import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.Money;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import io.vavr.control.Either;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FakePaymentGatewayAdapterTest {

    private final FakePaymentGatewayAdapter gateway = new FakePaymentGatewayAdapter();
    private final Money amount = new Money(new BigDecimal("29.99"));

    @Test
    void approvedPrefixYieldsConfirmationEchoingTokenAndAmount() {
        PaymentToken token = new PaymentToken("approved-visa-1");

        Either<Failure, PaymentConfirmation> result = gateway.charge(token, amount);

        assertThat(result.isRight()).isTrue();
        PaymentConfirmation confirmation = result.get();
        assertThat(confirmation.paymentToken()).isEqualTo(token);
        assertThat(confirmation.amount()).isEqualTo(amount);
        assertThat(confirmation.confirmationReference()).isNotBlank();
    }

    @Test
    void insufficientFundsPrefixYieldsBankDecline() {
        Either<Failure, PaymentConfirmation> result =
                gateway.charge(new PaymentToken("insufficient-funds-1"), amount);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(Failure.PaymentRejected.class);
        Failure.PaymentRejected rejection = (Failure.PaymentRejected) result.getLeft();
        assertThat(rejection.reason()).containsIgnoringCase("insufficient funds");
    }

    @Test
    void gatewayErrorPrefixYieldsInfrastructureFailureDistinctFromBankDecline() {
        Failure.PaymentRejected gatewayFailure = (Failure.PaymentRejected)
                gateway.charge(new PaymentToken("gateway-error-1"), amount).getLeft();
        Failure.PaymentRejected bankDecline = (Failure.PaymentRejected)
                gateway.charge(new PaymentToken("insufficient-funds-1"), amount).getLeft();

        assertThat(gatewayFailure.reason()).containsIgnoringCase("gateway");
        assertThat(gatewayFailure.reason()).isNotEqualTo(bankDecline.reason());
    }

    @Test
    void unrecognizedTokenIsRejectedByDefault() {
        Either<Failure, PaymentConfirmation> result =
                gateway.charge(new PaymentToken("tok_live_random_no_prefix"), amount);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(Failure.PaymentRejected.class);
    }

    @Test
    void prefixMatchingIsCaseInsensitive() {
        assertThat(gateway.charge(new PaymentToken("APPROVED-VISA-1"), amount).isRight()).isTrue();
        assertThat(gateway.charge(new PaymentToken("Insufficient-Funds-1"), amount).isLeft()).isTrue();
    }

    @Test
    void sameTokenAndAmountAlwaysProduceTheSameConfirmationReference() {
        PaymentToken token = new PaymentToken("approved-deterministic");

        String first = gateway.charge(token, amount).get().confirmationReference();
        String second = gateway.charge(token, amount).get().confirmationReference();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentAmountsProduceDifferentConfirmationReferencesForTheSameToken() {
        PaymentToken token = new PaymentToken("approved-deterministic");

        String forCheap = gateway.charge(token, new Money(new BigDecimal("10.00"))).get().confirmationReference();
        String forPricey = gateway.charge(token, new Money(new BigDecimal("999.00"))).get().confirmationReference();

        assertThat(forCheap).isNotEqualTo(forPricey);
    }
}
