package com.ecomm.user.config;

import com.ecomm.user.exception.UnauthorizedException;
import com.ecomm.user.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT authentication filter — runs once per request.
 *
 * <p>What it does:
 * <ol>
 *   <li>Extracts the Bearer token from the {@code Authorization} header.</li>
 *   <li>Validates the signature and expiry via {@link JwtService}.</li>
 *   <li>Checks the Redis blacklist — if the token's {@code jti} is present the
 *       request is rejected with 401 (the user has logged out).</li>
 *   <li>Populates the Spring {@link SecurityContextHolder} so downstream
 *       {@code @PreAuthorize} checks work normally.</li>
 *   <li>Injects {@code traceId} (= jti) into the MDC for structured logging.</li>
 * </ol>
 *
 * <p>Requests to public paths ({@code /auth/**}) are excluded by
 * {@link SecurityConfig#securityFilterChain} before they reach this filter.
 */
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final JwtService jwtService;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                Claims claims = jwtService.validateAndExtractClaims(token);

                // ── blacklist check (logout) ──────────────────────────
                String jti = jwtService.extractJti(claims);
                if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti))) {
                    log.debug("Rejected blacklisted token jti={}", jti);
                    sendUnauthorized(response, "Token has been revoked");
                    return;
                }

                // ── build authentication ──────────────────────────────
                UUID userId = jwtService.extractUserId(claims);
                Set<String> roles = jwtService.extractRoles(claims);

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                auth.setDetails(request);

                SecurityContextHolder.getContext().setAuthentication(auth);

                // ── MDC tracing ───────────────────────────────────────
                MDC.put("traceId", jti);
                MDC.put("userId", userId.toString());

            } catch (UnauthorizedException ex) {
                SecurityContextHolder.clearContext();
                sendUnauthorized(response, ex.getMessage());
                return;
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
            MDC.remove("userId");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}"
        );
    }
}
