package com.ecommerce.marketplace.infrastructure.config;

import com.ecommerce.marketplace.application.ports.out.EventPublisherPort;
import com.ecommerce.marketplace.infrastructure.messaging.KafkaEventPublisherAdapter;
import com.ecommerce.marketplace.infrastructure.messaging.OutboxEventJpaRepository;
import com.ecommerce.marketplace.infrastructure.messaging.OutboxRelayScheduler;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * Wiring for the transactional-outbox messaging path (US-15). Owns the Kafka producer, the
 * {@link EventPublisherPort} outbox adapter and the {@link OutboxRelayScheduler}, keeping all
 * Kafka/spring-kafka types confined to {@code infrastructure} (never {@code application}/{@code domain}).
 *
 * <p>{@link EnableScheduling} is placed here rather than on {@code MarketplaceApplication} so the
 * scheduling concern stays co-located with the only scheduled task in the project. The relay is
 * <strong>always active</strong> (not profile-gated): the outbox is core to the app's eventual
 * consistency guarantee, so unlike the optional Redis cache (US-14) there is no configuration in
 * which it should be off. Its tick interval is externalized
 * ({@code marketplace.outbox.relay.fixed-delay-ms}, default 1000ms).</p>
 *
 * <p>A String/String {@link KafkaTemplate} is declared explicitly (rather than relying on the
 * autoconfigured {@code Object/Object} template): the outbox already serialized the event to a
 * JSON string, so the producer only ever ships a pre-serialized {@code String} value keyed by the
 * aggregate id, and String serializers make that contract explicit.</p>
 */
@Configuration
@EnableScheduling
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
}
