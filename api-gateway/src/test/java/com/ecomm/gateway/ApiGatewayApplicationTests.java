package com.ecomm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Context load smoke test.
 *
 * <p>Supplies the minimum required properties so the context can start
 * without a live Redis or Eureka. Redis is mocked via @MockBean so
 * the ReactiveStringRedisTemplate dependency in JwtAuthenticationFilter
 * is satisfied without a real Redis connection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-context-load-at-least-32-chars",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "eureka.client.enabled=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class ApiGatewayApplicationTests {

    // Provide a mock ReactiveStringRedisTemplate so the JwtAuthenticationFilter
    // can be wired without a real Redis connection during the context load test.
    @MockBean
    ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Test
    void contextLoads() {
        // Verifies the Spring application context starts successfully with all
        // auto-configurations applied (Gateway, Security, Resilience4j, filters).
        // A startup failure will fail this test automatically.
    }
}
