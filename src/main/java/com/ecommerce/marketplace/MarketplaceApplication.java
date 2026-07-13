package com.ecommerce.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;

/**
 * The Redis autoconfigurations are excluded here so that Spring Boot never eagerly builds a
 * {@code RedisConnectionFactory} just because {@code spring-data-redis}/Lettuce is on the
 * classpath. The read-through cache (US-14) is opt-in via the {@code cache} profile, and all its
 * Redis beans are declared explicitly in {@code infrastructure.config.RedisCacheConfig} behind
 * {@code @Profile("cache")}. Without this exclusion the app would attempt a Redis connection at
 * boot even with the profile off, breaking startup when Redis is not running.
 */
@SpringBootApplication(exclude = {
        DataRedisAutoConfiguration.class,
        DataRedisReactiveAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
})
public class MarketplaceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketplaceApplication.class, args);
	}

}
