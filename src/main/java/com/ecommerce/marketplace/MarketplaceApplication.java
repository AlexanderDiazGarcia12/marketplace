package com.ecommerce.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;

/**
 * The Redis autoconfigurations are excluded so Spring Boot never eagerly builds a
 * {@code RedisConnectionFactory} just because Lettuce is on the classpath. The read-through cache
 * is opt-in via the {@code cache} profile, with its Redis beans declared behind
 * {@code @Profile("cache")}; without this exclusion the app would attempt a Redis connection at
 * boot even with the profile off.
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
