package com.b2chat.ordermanagement.redis.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

@Configuration
public class RedisConnectionWarmupConfig {
    // Spring Data Redis eagerly opens the shared *imperative* connection during context startup,
    // but not the reactive one: Lettuce opens that one lazily, on first use, with a blocking
    // CompletableFuture#get() call. Without this warm-up, that blocking call happens inside the
    // first real reactive request instead of during application startup.
    @Bean
    public ApplicationRunner warmUpReactiveRedisConnection(ReactiveRedisConnectionFactory connectionFactory) {
        return args -> connectionFactory.getReactiveConnection().ping().block();
    }
}
