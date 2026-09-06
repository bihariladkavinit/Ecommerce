package com.ecomm.gateway.filter;

import com.ecomm.gateway.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 *
 * <p>No Spring context is loaded — all dependencies are provided via Mockito
 * so these tests run fast and without infrastructure.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    // Must be ≥ 32 bytes for HS256
    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-at-least-32-chars";
    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthenticationFilter filter;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET);
        filter = new JwtAuthenticationFilter(jwtUtil, redisTemplate, new ObjectMapper());
        // Lenient: this stub is only exercised by tests that reach the filter chain.
        // Tests that short-circuit (401 paths) never call chain.filter(), so Mockito
        // strict stubbing would flag it as unnecessary without lenient().
        lenient().when(chain.filter(anyExchange())).thenReturn(Mono.empty());
    }

    // ── Public path tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Public auth path passes through without a token")
    void publicAuthPath_noToken_passesThrough() {
        var exchange = exchangeFor("GET", "/api/v1/auth/login");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("GET /api/v1/products/** passes through without a token")
    void publicProductGetPath_noToken_passesThrough() {
        var exchange = exchangeFor("GET", "/api/v1/products/123");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("POST /api/v1/products/** requires a token")
    void productPostPath_noToken_returns401() {
        var exchange = exchangeFor("POST", "/api/v1/products/");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Missing / malformed header tests ─────────────────────────────

    @Test
    @DisplayName("Missing Authorization header on protected path → 401")
    void missingAuthHeader_returns401() {
        var exchange = exchangeFor("GET", "/api/v1/users/me");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Authorization header without 'Bearer ' prefix → 401")
    void malformedAuthHeader_returns401() {
        var request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Invalid token tests ───────────────────────────────────────────

    @Test
    @DisplayName("Completely invalid token string → 401")
    void invalidToken_returns401() {
        var request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt")
                .build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Expired token → 401")
    void expiredToken_returns401() {
        String expiredToken = Jwts.builder()
                .subject("user-123")
                .id("jti-expired")
                .expiration(new Date(System.currentTimeMillis() - 60_000)) // 1 min in the past
                .signWith(SIGNING_KEY)
                .compact();

        var request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Blacklist tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Valid token whose JTI is in Redis blacklist → 401")
    void blacklistedToken_returns401() {
        String jti = "jti-blacklisted";
        String token = buildValidToken("user-456", jti, "ROLE_USER");

        when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(Mono.just(true));

        var request = MockServerHttpRequest.get("/api/v1/cart")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Happy path tests ──────────────────────────────────────────────

    @Test
    @DisplayName("Valid, non-blacklisted token → passes through with X-User-Id and X-User-Roles headers")
    void validToken_notBlacklisted_passesThroughWithHeaders() {
        String jti = "jti-valid";
        String userId = "user-789";
        String roles = "ROLE_USER";
        String token = buildValidToken(userId, jti, roles);

        when(redisTemplate.hasKey("blacklist:" + jti)).thenReturn(Mono.just(false));

        // Capture the mutated exchange forwarded to the chain
        var request = MockServerHttpRequest.get("/api/v1/cart")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        var exchange = MockServerWebExchange.from(request);

        // Override chain mock to capture the mutated request
        when(chain.filter(anyExchange())).thenAnswer(inv -> {
            MockServerWebExchange mutated = (MockServerWebExchange) inv.getArgument(0);
            assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(userId);
            assertThat(mutated.getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo(roles);
            assertThat(mutated.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Response status should remain null (not set by the filter on success)
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String buildValidToken(String userId, String jti, String roles) {
        return Jwts.builder()
                .subject(userId)
                .id(jti)
                .claim("roles", roles)
                .expiration(new Date(System.currentTimeMillis() + 3_600_000)) // 1 hour
                .signWith(SIGNING_KEY)
                .compact();
    }

    private MockServerWebExchange exchangeFor(String method, String path) {
        MockServerHttpRequest request = switch (method.toUpperCase()) {
            case "POST" -> MockServerHttpRequest.post(path).build();
            case "PUT"  -> MockServerHttpRequest.put(path).build();
            default     -> MockServerHttpRequest.get(path).build();
        };
        return MockServerWebExchange.from(request);
    }

    /** Mockito matcher for any {@link org.springframework.web.server.ServerWebExchange}. */
    private static org.springframework.web.server.ServerWebExchange anyExchange() {
        return org.mockito.ArgumentMatchers.any();
    }
}
