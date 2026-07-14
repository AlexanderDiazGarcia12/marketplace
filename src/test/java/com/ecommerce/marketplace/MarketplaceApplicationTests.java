package com.ecommerce.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Full-context smoke test. {@code spring.docker.compose.skip.in-tests} defaults to {@code true},
 * so Boot's Docker Compose support otherwise no-ops under the test runner and leaves the datasource
 * unconfigured — the context then fails to start with "Failed to determine a suitable driver class".
 * Overriding it to {@code false} lets this test auto-discover the {@code compose.yaml} Postgres/Kafka
 * services exactly like {@code spring-boot:run}, matching the convention the persistence integration
 * tests already established.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.docker.compose.skip.in-tests=false")
class MarketplaceApplicationTests {

	@Test
	void contextLoads() {
	}

}
