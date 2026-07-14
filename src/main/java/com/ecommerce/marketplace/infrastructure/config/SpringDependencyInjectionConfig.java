package com.ecommerce.marketplace.infrastructure.config;

import com.ecommerce.marketplace.application.ports.in.CreateProductUseCase;
import com.ecommerce.marketplace.application.ports.in.DeleteProductUseCase;
import com.ecommerce.marketplace.application.ports.in.GetProductUseCase;
import com.ecommerce.marketplace.application.ports.in.ImportProductsUseCase;
import com.ecommerce.marketplace.application.ports.in.SearchProductUseCase;
import com.ecommerce.marketplace.application.ports.in.UpdateProductUseCase;
import com.ecommerce.marketplace.application.ports.out.EventPublisherPort;
import com.ecommerce.marketplace.application.ports.out.ImportJobRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.application.service.CreateProductService;
import com.ecommerce.marketplace.application.service.DeleteProductService;
import com.ecommerce.marketplace.application.service.GetProductService;
import com.ecommerce.marketplace.application.service.ImportProductsService;
import com.ecommerce.marketplace.application.service.SearchProductService;
import com.ecommerce.marketplace.application.service.UpdateProductService;
import com.ecommerce.marketplace.infrastructure.persistence.PostgreSQLImportJobRepositoryAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.PostgreSQLProductRepositoryAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.ProductCacheCodec;
import com.ecommerce.marketplace.infrastructure.persistence.RedisCachingProductRepositoryAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.SpringDataProductJpaRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Bean
    TransactionTemplate marketplaceTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @Profile("!cache")
    ProductRepositoryPort productRepositoryPort(
            SpringDataProductJpaRepository jpaRepository,
            EntityManager entityManager,
            TransactionTemplate transactionTemplate) {
        return new PostgreSQLProductRepositoryAdapter(jpaRepository, entityManager, transactionTemplate);
    }

    @Bean
    @Profile("cache")
    ProductRepositoryPort cachingProductRepositoryPort(
            SpringDataProductJpaRepository jpaRepository,
            EntityManager entityManager,
            TransactionTemplate transactionTemplate,
            StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        PostgreSQLProductRepositoryAdapter postgresAdapter =
                new PostgreSQLProductRepositoryAdapter(jpaRepository, entityManager, transactionTemplate);
        return new RedisCachingProductRepositoryAdapter(postgresAdapter, redis, new ProductCacheCodec(objectMapper));
    }

    @Bean
    CreateProductUseCase createProductUseCase(ProductRepositoryPort productRepository) {
        return new CreateProductService(productRepository);
    }

    @Bean
    GetProductUseCase getProductUseCase(ProductRepositoryPort productRepository) {
        return new GetProductService(productRepository);
    }

    @Bean
    UpdateProductUseCase updateProductUseCase(ProductRepositoryPort productRepository) {
        return new UpdateProductService(productRepository);
    }

    @Bean
    DeleteProductUseCase deleteProductUseCase(ProductRepositoryPort productRepository) {
        return new DeleteProductService(productRepository);
    }

    @Bean
    SearchProductUseCase searchProductUseCase(ProductRepositoryPort productRepository) {
        return new SearchProductService(productRepository);
    }

    @Bean
    ImportJobRepositoryPort importJobRepositoryPort(EntityManager entityManager) {
        return new PostgreSQLImportJobRepositoryAdapter(entityManager);
    }

    @Bean
    ImportProductsUseCase importProductsUseCase(
            ImportJobRepositoryPort importJobRepository, EventPublisherPort eventPublisher) {
        return new ImportProductsService(importJobRepository, eventPublisher);
    }
}
