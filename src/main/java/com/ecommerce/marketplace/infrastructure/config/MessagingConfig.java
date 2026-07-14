package com.ecommerce.marketplace.infrastructure.config;

import com.ecommerce.marketplace.application.ports.out.EventPublisherPort;
import com.ecommerce.marketplace.application.ports.out.ImportErrorRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ImportJobRepositoryPort;
import com.ecommerce.marketplace.application.ports.out.ProductRepositoryPort;
import com.ecommerce.marketplace.application.service.CsvProductRowValidator;
import com.ecommerce.marketplace.infrastructure.messaging.KafkaEventPublisherAdapter;
import com.ecommerce.marketplace.infrastructure.messaging.OutboxEventJpaRepository;
import com.ecommerce.marketplace.infrastructure.messaging.OutboxRelayScheduler;
import com.ecommerce.marketplace.infrastructure.messaging.ProductImportConsumer;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * Wiring for the whole Kafka messaging path. Owns the producer + outbox side (US-15: the
 * {@link EventPublisherPort} outbox adapter and the {@link OutboxRelayScheduler}) and the consumer
 * side (US-17: the manual-ack listener container factory and the {@link ProductImportConsumer}),
 * keeping all Kafka/spring-kafka types confined to {@code infrastructure} (never
 * {@code application}/{@code domain}).
 *
 * <p>{@link EnableScheduling} is placed here rather than on {@code MarketplaceApplication} so the
 * scheduling concern stays co-located with the only scheduled task in the project; {@link EnableKafka}
 * turns on {@code @KafkaListener} processing for the US-17 consumer. The relay is
 * <strong>always active</strong> (not profile-gated): the outbox is core to the app's eventual
 * consistency guarantee, so unlike the optional Redis cache (US-14) there is no configuration in
 * which it should be off. Its tick interval is externalized
 * ({@code marketplace.outbox.relay.fixed-delay-ms}, default 1000ms).</p>
 *
 * <p>A String/String {@link KafkaTemplate} is declared explicitly (rather than relying on the
 * autoconfigured {@code Object/Object} template): the outbox already serialized the event to a
 * JSON string, so the producer only ever ships a pre-serialized {@code String} value keyed by the
 * aggregate id, and String serializers make that contract explicit. The import consumer factory is
 * likewise String/String with <strong>auto-commit disabled and {@code AckMode.MANUAL}</strong>: the
 * US-17 worker acks the offset only after the whole file reaches a terminal state, so a mid-file
 * crash triggers a real Kafka redelivery rather than a silent loss (the consumer is idempotent by
 * construction to absorb it).</p>
 */
@Configuration
@EnableScheduling
@EnableKafka
public class MessagingConfig {

    private final String bootstrapServers;

    public MessagingConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    ProducerFactory<String, String> outboxProducerFactory() {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, String> outboxKafkaTemplate(ProducerFactory<String, String> outboxProducerFactory) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }

    @Bean
    EventPublisherPort eventPublisherPort(EntityManager entityManager, ObjectMapper objectMapper) {
        return new KafkaEventPublisherAdapter(entityManager, objectMapper);
    }

    @Bean
    OutboxRelayScheduler outboxRelayScheduler(
            OutboxEventJpaRepository outboxRepository,
            KafkaTemplate<String, String> outboxKafkaTemplate,
            TransactionTemplate transactionTemplate) {
        return new OutboxRelayScheduler(outboxRepository, outboxKafkaTemplate, transactionTemplate);
    }

    @Bean
    ConsumerFactory<String, String> importConsumerFactory(
            @Value("${marketplace.import.consumer.group-id:product-import-worker}") String groupId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> importListenerContainerFactory(
            ConsumerFactory<String, String> importConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(importConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    @Bean
    ProductImportConsumer productImportConsumer(
            ObjectMapper objectMapper,
            ImportJobRepositoryPort importJobRepository,
            ImportErrorRepositoryPort importErrorRepository,
            ProductRepositoryPort productRepository,
            EventPublisherPort eventPublisher,
            CsvProductRowValidator csvProductRowValidator,
            TransactionTemplate transactionTemplate) {
        return new ProductImportConsumer(
                objectMapper,
                importJobRepository,
                importErrorRepository,
                productRepository,
                eventPublisher,
                csvProductRowValidator,
                transactionTemplate);
    }
}
