package com.ecomm.gateway.filter;

import com.ecomm.gateway.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global JWT authentication filter.
 *
 * <p>Runs before all route filters (order {@code -1}).  For every incoming request:
 * <ol>
 *   <li>Public paths are passed through without any token check.</li>
 *   <li>Absence of an {@code Authorization: Bearer} header → 401.</li>
 *   <li>Invalid / expired token → 401.</li>
 *   <li>Token JTI present in Redis blacklist (logged-out token) → 401.</li>
 *   <li>Valid token: strip the original {@code Authorization} header, add
 *       {@code X-User-Id} and {@code X-User-Roles} trusted internal headers,
 *       then continue the filter chain.</li>
 * </ol>
 *
 * <p>All 401 responses use the standard error envelope defined in the design doc.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    // Paths that bypass JWT validation entirely
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/shipments/track/",
            "/actuator/",
            "/fallback"
    );

    // GET-only public paths (product browsing, category listing)
    private static final List<String> PUBLIC_GET_PREFIXES = List.of(
            "/api/v1/products/",
            "/api/v1/categories/"
    );

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   ReactiveStringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        // Run before Spring Cloud Gateway's routing filter (order 0)
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        // 1. Pass through all unconditionally public paths
        if (isPublicPath(path, method)) {
            return chain.filter(exchange);
        }

        // 2. Require Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header", path);
        }

        String token = authHeader.substring(7);

        // 3. Validate signature and expiry synchronously (cheap CPU work)
        if (!jwtUtil.isTokenValid(token)) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token", path);
        }

        // Extract claims once — safe because isTokenValid already passed
        String userId;
        String roles;
        String jti;
        try {
            userId = jwtUtil.extractUserId(token);
            roles  = jwtUtil.extractRoles(token);
            jti    = jwtUtil.extractJti(token);
        } catch (JwtException e) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    "Token parsing failed", path);
        }

        // 4. Check Redis blacklist (async — token revoked on logout)
        String blacklistKey = "blacklist:" + jti;
        return redisTemplate.hasKey(blacklistKey)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                                "Token has been revoked", path);
                    }

                    // 5. Mutate request: remove Authorization, add trusted internal headers
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header(HttpHeaders.AUTHORIZATION)   // strips the header
                            .header("X-User-Id", userId)
                            .header("X-User-Roles", roles)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private boolean isPublicPath(String path, String method) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix) || path.equals(prefix.stripTrailing())) {
                return true;
            }
        }
        // Products and categories are public for GET only
        if ("GET".equalsIgnoreCase(method)) {
            for (String prefix : PUBLIC_GET_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Writes a JSON 401 response using the project's standard error envelope.
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange,
                                          HttpStatus status,
                                          String message,
                                          String path) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.name());
        body.put("message", message);
        body.put("path", path);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":401,\"error\":\"UNAUTHORIZED\"}").getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
