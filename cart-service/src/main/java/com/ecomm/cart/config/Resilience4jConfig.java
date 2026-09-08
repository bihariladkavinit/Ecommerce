package com.ecomm.cart.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Programmatic Resilience4j configuration for the three Feign circuit breakers.
 *
 * <p>Spring Cloud OpenFeign uses {@link Resilience4JCircuitBreakerFactory} to wrap
 * each Feign client. The factory resolves circuit breaker config by the CB instance
 * ID, which defaults to {@code "<FeignClientName>#<methodName>(<paramTypes>)"} when
 * alphanumeric IDs are disabled. We use per-client customizers keyed to the service
 * name prefix so the same settings apply to all methods on each client.
 *
 * <p>The values here mirror those in {@code application.yml} under
 * {@code resilience4j.*} — the YAML config drives the standalone Resilience4j
 * beans (used by {@code @CircuitBreaker} annotations), while these customizers
 * configure the factory used by Feign's circuit breaker integration. Both need
 * to be consistent.
 *
 * <p>Fallback chain per client (design doc §8):
 * <ul>
 *   <li>product-service  : CB(50% threshold, 10 s open) + TimeLimiter(2 s) + Retry(2)</li>
 *   <li>inventory-service: CB(50% threshold, 10 s open) + TimeLimiter(2 s) + Retry(2) + Bulkhead(10)</li>
 *   <li>order-service    : CB(50% threshold, 15 s open) + TimeLimiter(3 s) + Retry(1)</li>
 * </ul>
 */
@Configuration
public class Resilience4jConfig {

    // ── product-service ───────────────────────────────────────────────

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> productServiceCustomizer() {
        return factory -> factory.configure(
                builder -> builder
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowSize(10)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofSeconds(10))
                                .permittedNumberOfCallsInHalfOpenState(3)
                                .build())
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(2))
                                .build()),
                "product-service"
        );
    }

    // ── inventory-service ─────────────────────────────────────────────

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> inventoryServiceCustomizer() {
        return factory -> factory.configure(
                builder -> builder
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowSize(10)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofSeconds(10))
                                .permittedNumberOfCallsInHalfOpenState(3)
                                .build())
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(2))
                                .build()),
                "inventory-service"
        );
    }

    // ── order-service ─────────────────────────────────────────────────

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> orderServiceCustomizer() {
        return factory -> factory.configure(
                builder -> builder
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowSize(10)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofSeconds(15))
                                .permittedNumberOfCallsInHalfOpenState(3)
                                .build())
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(3))
                                .build()),
                "order-service"
        );
    }

    // ── Default (safety net for any client not explicitly configured) ──

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id ->
                new Resilience4JConfigBuilder(id)
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowSize(10)
                                .failureRateThreshold(50)
                                .waitDurationInOpenState(Duration.ofSeconds(10))
                                .build())
                        .timeLimiterConfig(TimeLimiterConfig.custom()
                                .timeoutDuration(Duration.ofSeconds(2))
                                .build())
                        .build());
    }
}
