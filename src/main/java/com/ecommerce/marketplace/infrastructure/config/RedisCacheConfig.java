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
 * Redis wiring for the read-through cache, active only under the {@code cache} Spring profile. Every
 * Redis bean lives here behind {@code @Profile("cache")} (and {@code RedisAutoConfiguration} is
 * excluded on {@link com.ecommerce.marketplace.MarketplaceApplication}) so the app never tries to
 * reach Redis at boot when the profile is off. A {@link StringRedisTemplate} suffices because the
 * caching adapter serializes its own JSON entries; Redis stays a pure read cache, never a source of
 * truth, lock, or idempotency store.
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
