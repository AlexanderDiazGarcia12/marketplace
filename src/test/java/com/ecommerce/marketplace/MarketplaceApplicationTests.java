package com.ecommerce.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Full-context smoke test. Overriding {@code spring.docker.compose.skip.in-tests} to {@code false}
 * lets it auto-discover the {@code compose.yaml} Postgres/Kafka services like {@code spring-boot:run};
 * otherwise the datasource is left unconfigured and the context fails to start.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.docker.compose.skip.in-tests=false")
class MarketplaceApplicationTests {

	@Test
	void contextLoads() {
	}

}
