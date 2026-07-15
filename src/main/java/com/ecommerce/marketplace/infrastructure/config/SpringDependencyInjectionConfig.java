package com.ecommerce.marketplace.infrastructure.config;

import com.ecommerce.marketplace.application.ports.in.CreateProductUseCase;
import com.ecommerce.marketplace.application.ports.in.DeleteProductUseCase;
import com.ecommerce.marketplace.application.ports.in.GetImportJobStatusUseCase;
import com.ecommerce.marketplace.application.ports.in.GetOrderUseCase;
import com.ecommerce.marketplace.application.ports.in.GetProductUseCase;
import com.ecommerce.marketplace.application.ports.in.ImportProductsUseCase;
import com.ecommerce.marketplace.application.ports.in.ListOrdersUseCase;
import com.ecommerce.marketplace.application.ports.in.SearchProductUseCase;
import com.ecommerce.marketplace.application.ports.in.UpdateProductUseCase;
import com.ecommerce.marketplace.application.ports.out.EventPublisherPort;
import com.ecommerce.marketplace.application.ports.out.IdempotencyStorePort;
import com.ecommerce.marketplace.application.ports.out.ImportErrorRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ImportJobRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.OrderRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.PaymentGatewayPort;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.PurchaseSnapshotCodec;
import com.ecommerce.marketplace.application.service.CreateProductService;
import com.ecommerce.marketplace.application.service.CsvProductRowValidator;
import com.ecommerce.marketplace.application.service.DeleteProductService;
import com.ecommerce.marketplace.application.service.GetImportJobStatusService;
import com.ecommerce.marketplace.application.service.GetOrderService;
import com.ecommerce.marketplace.application.service.GetProductService;
import com.ecommerce.marketplace.application.service.ImportProductsService;
import com.ecommerce.marketplace.application.service.ListOrdersService;
import com.ecommerce.marketplace.application.service.PurchaseProductService;
import com.ecommerce.marketplace.application.service.SearchProductService;
import com.ecommerce.marketplace.application.service.UpdateProductService;
import com.ecommerce.marketplace.infrastructure.payment.FakePaymentGatewayAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.JacksonPurchaseSnapshotCodec;
import com.ecommerce.marketplace.infrastructure.persistence.PostgreSQLImportErrorRepositoryAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.PostgreSQLIdempotencyStoreAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.PostgreSQLImportJobRepositoryAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.PostgreSQLOrderRepositoryAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.PostgreSQLProductRepositoryAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.ProductCacheCodec;
import com.ecommerce.marketplace.infrastructure.persistence.RedisCachingProductRepositoryAdapter;
import com.ecommerce.marketplace.infrastructure.persistence.SpringDataIdempotencyKeyJpaRepository;
import com.ecommerce.marketplace.infrastructure.persistence.SpringDataImportJobErrorJpaRepository;
import com.ecommerce.marketplace.infrastructure.persistence.SpringDataImportJobJpaRepository;
import com.ecommerce.marketplace.infrastructure.persistence.SpringDataOrderJpaRepository;
import com.ecommerce.marketplace.infrastructure.persistence.SpringDataProductJpaRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
    @Primary
    TransactionTemplate marketplaceTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    /**
     * Dedicated {@code REQUIRES_NEW} template for the checkout compensating write (US-22): after the
     * main purchase transaction rolls back on a declined payment (restoring stock), this genuinely
     * independent transaction persists the {@code REJECTED} order and completes the idempotency key,
     * so those records survive the main transaction's rollback. {@code @Primary} on
     * {@code marketplaceTransactionTemplate} keeps every existing {@code TransactionTemplate}
     * injection resolving to the joining ({@code REQUIRED}) template; only {@code CheckoutController}
     * asks for this one by name via {@code @Qualifier}.
     */
    @Bean
    TransactionTemplate rejectionTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
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
    ImportJobRepositoryPort importJobRepositoryPort(
            EntityManager entityManager, SpringDataImportJobJpaRepository importJobJpaRepository) {
        return new PostgreSQLImportJobRepositoryAdapter(entityManager, importJobJpaRepository);
    }

    @Bean
    ImportErrorRepositoryPort importErrorRepositoryPort(
            SpringDataImportJobErrorJpaRepository importJobErrorJpaRepository, ObjectMapper objectMapper) {
        return new PostgreSQLImportErrorRepositoryAdapter(importJobErrorJpaRepository, objectMapper);
    }

    @Bean
    CsvProductRowValidator csvProductRowValidator() {
        return new CsvProductRowValidator();
    }

    @Bean
    ImportProductsUseCase importProductsUseCase(
            ImportJobRepositoryPort importJobRepository, EventPublisherPort eventPublisher) {
        return new ImportProductsService(importJobRepository, eventPublisher);
    }

    @Bean
    PaymentGatewayPort paymentGatewayPort() {
        return new FakePaymentGatewayAdapter();
    }

    @Bean
    IdempotencyStorePort idempotencyStorePort(
            SpringDataIdempotencyKeyJpaRepository idempotencyKeyJpaRepository,
            PlatformTransactionManager transactionManager) {
        return new PostgreSQLIdempotencyStoreAdapter(idempotencyKeyJpaRepository, transactionManager);
    }

    @Bean
    GetImportJobStatusUseCase getImportJobStatusUseCase(
            ImportJobRepositoryPort importJobRepository, ImportErrorRepositoryPort importErrorRepository) {
        return new GetImportJobStatusService(importJobRepository, importErrorRepository);
    }

    @Bean
    OrderRepositoryPort orderRepositoryPort(
            EntityManager entityManager,
            SpringDataOrderJpaRepository orderJpaRepository,
            TransactionTemplate transactionTemplate) {
        return new PostgreSQLOrderRepositoryAdapter(entityManager, orderJpaRepository, transactionTemplate);
    }

    @Bean
    ListOrdersUseCase listOrdersUseCase(OrderRepositoryPort orderRepository) {
        return new ListOrdersService(orderRepository);
    }

    @Bean
    GetOrderUseCase getOrderUseCase(OrderRepositoryPort orderRepository, ProductRepositoryPort productRepository) {
        return new GetOrderService(orderRepository, productRepository);
    }

    @Bean
    PurchaseSnapshotCodec purchaseSnapshotCodec(ObjectMapper objectMapper) {
        return new JacksonPurchaseSnapshotCodec(objectMapper);
    }

    @Bean
    PurchaseProductService purchaseProductService(
            IdempotencyStorePort idempotencyStore,
            ProductRepositoryPort productRepository,
            PaymentGatewayPort paymentGateway,
            OrderRepositoryPort orderRepository,
            EventPublisherPort eventPublisher,
            PurchaseSnapshotCodec snapshotCodec) {
        return new PurchaseProductService(
                idempotencyStore, productRepository, paymentGateway, orderRepository, eventPublisher, snapshotCodec);
    }
}
