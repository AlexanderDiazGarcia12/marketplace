package com.ecommerce.marketplace.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Single composition root for {@code application.service} beans (US-05).
 *
 * <p>The {@code domain} and {@code application} layers are plain Java: no {@code @Service},
 * {@code @Component}, {@code @Repository} or any other Spring stereotype annotation may appear
 * in those packages (enforced by
 * {@code com.ecommerce.marketplace.architecture.HexagonalArchitectureTest}). Instead, every
 * concrete application service is wired here, in {@code infrastructure}, via an explicit
 * {@code @Bean} factory method that hand-assembles the service and its out-port dependencies.</p>
 *
 * <p>Convention for future stories (US-09, US-13, US-16/17, US-22) adding a service that
 * implements one of the {@code application.ports.in} use cases:</p>
 *
 * <pre>{@code
 * @Bean
 * CreateProductUseCase createProductUseCase(ProductRepositoryPort productRepository) {
 *     return new CreateProductService(productRepository);
 * }
 * }</pre>
 *
 * <p>The service class itself (e.g. {@code CreateProductService}) stays an unannotated plain
 * class in {@code application.service}; only this configuration class knows about Spring. This
 * keeps the core hexagon (domain + application) portable and testable without a Spring context,
 * while infrastructure remains the sole place where wiring decisions live.</p>
 *
 * <p>This class is intentionally near-empty until the first concrete service exists — there is
 * nothing to wire before US-09 introduces {@code CreateProductService}, and adapters for the
 * out-ports ({@code ProductRepositoryPort}, {@code OrderRepositoryPort},
 * {@code IdempotencyStorePort}, {@code PaymentGatewayPort}, {@code EventPublisherPort}) do not
 * exist yet either. Its purpose in this story is to establish the convention and package
 * location, not to register beans.</p>
 */
@Configuration
public class SpringDependencyInjectionConfig {
}
