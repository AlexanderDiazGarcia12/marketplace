package com.ecommerce.marketplace.infrastructure.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis wiring for the read-through cache (US-14), active <strong>only</strong> under the
 * {@code cache} Spring profile. Spring Boot's {@code RedisAutoConfiguration} is excluded on
 * {@link com.ecommerce.marketplace.MarketplaceApplication} so that merely having
 * {@code spring-data-redis}/Lettuce on the classpath never eagerly builds a
 * {@link RedisConnectionFactory} — without that exclusion the app would try to reach Redis at
 * boot even when the profile is off, breaking startup when no Redis is running. Every Redis bean
 * therefore lives here behind {@code @Profile("cache")}: no profile, no connection factory, no
 * connection attempt.
 *
 * <p>A {@link StringRedisTemplate} is enough: the caching adapter stores cache entries as JSON
 * strings it serializes itself, so it never needs value serializers configured on the template.
 * RNF-2 keeps Redis a pure read cache — it is never a source of truth, a lock, or an idempotency
 * store.</p>
 */
@Configuration
@Profile("cache")
public class RedisCacheConfig {

    private final String host;
    private final int port;

    public RedisCacheConfig(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        this.host = host;
        this.port = port;
    }

    @Bean
    RedisConnectionFactory redisConnectionFactory() {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(1))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build())
                        .build())
                .build();
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
