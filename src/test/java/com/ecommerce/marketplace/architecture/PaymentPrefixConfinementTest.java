package com.ecommerce.marketplace.architecture;

import com.ecommerce.marketplace.domain.failure.Failure;
import com.ecommerce.marketplace.domain.model.order.PaymentToken;
import io.vavr.control.Either;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that the fake-gateway prefix semantics live only in {@code infrastructure.payment}, and
 * the domain never knows a prefix decides a charge outcome.
 *
 * <p>Two complementary proofs:</p>
 * <ol>
 *   <li><em>Agnosticism</em> — {@link PaymentToken#of(String)} validates purely on format
 *       (non-blank, length), never on whether a token looks "approved" or "rejected".</li>
 *   <li><em>Confinement</em> — no source file under {@code domain} contains any gateway prefix
 *       literal. A source scan (not bytecode) is used so even a stray comment is caught.</li>
 * </ol>
 */
class PaymentPrefixConfinementTest {

    private static final Path DOMAIN_SOURCE_ROOT =
            Path.of("src", "main", "java", "com", "ecommerce", "marketplace", "domain");

    private static final List<String> GATEWAY_PREFIXES =
            List.of("approved-", "insufficient-funds-", "gateway-error-");

    @Test
    void paymentTokenValidationIsBlindToGatewayPrefixes() {
        for (String prefix : GATEWAY_PREFIXES) {
            Either<Failure, PaymentToken> prefixed = PaymentToken.of(prefix + "abc");
            Either<Failure, PaymentToken> plain = PaymentToken.of("plain-abc");

            assertThat(prefixed.isRight())
                    .as("prefix '%s' must not change PaymentToken acceptance", prefix)
                    .isEqualTo(plain.isRight());
            assertThat(prefixed.isRight()).isTrue();
        }
    }

    @Test
    void blankTokenIsRejectedRegardlessOfBeingPrefixedOrNot() {
        assertThat(PaymentToken.of("   ").isLeft()).isTrue();
        assertThat(PaymentToken.of("").isLeft()).isTrue();
    }

    @Test
    void noDomainSourceFileMentionsAnyGatewayPrefix() throws IOException {
        assertThat(Files.isDirectory(DOMAIN_SOURCE_ROOT))
                .as("domain source root must exist at %s", DOMAIN_SOURCE_ROOT.toAbsolutePath())
                .isTrue();

        try (Stream<Path> sources = Files.walk(DOMAIN_SOURCE_ROOT)) {
            List<String> offenders = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::mentionsAnyGatewayPrefix)
                    .map(Path::toString)
                    .toList();

            assertThat(offenders)
                    .as("the domain must not reference any fake-gateway prefix; those live only in infrastructure.payment")
                    .isEmpty();
        }
    }

    private boolean mentionsAnyGatewayPrefix(Path javaFile) {
        String content = readLowerCase(javaFile);
        return GATEWAY_PREFIXES.stream().anyMatch(content::contains);
    }

    private String readLowerCase(Path javaFile) {
        try {
            return Files.readString(javaFile).toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read domain source file " + javaFile, e);
        }
    }
}
