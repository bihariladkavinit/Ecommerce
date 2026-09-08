package com.ecomm.notification.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads the trusted internal headers forwarded by the API Gateway after
 * it has already validated the JWT:
 * <ul>
 *   <li>{@code X-User-Id}    — authenticated user's UUID</li>
 *   <li>{@code X-User-Roles} — comma-separated roles, e.g. "ROLE_USER,ROLE_ADMIN"</li>
 * </ul>
 *
 * <p>The notification service only exposes an optional admin debug endpoint
 * ({@code GET /notifications}); all other activity is Kafka-driven. The
 * Gateway filter is still needed to enforce {@code ROLE_ADMIN} on that endpoint.
 *
 * <p>The {@code X-User-Id} is injected into the MDC as {@code userId} and
 * {@code traceId} so log lines for admin requests are correlated to the caller.
 */
@Slf4j
public class GatewayAuthFilter extends OncePerRequestFilter {

    static final String HEADER_USER_ID    = "X-User-Id";
    static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String userId      = request.getHeader(HEADER_USER_ID);
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);

        if (StringUtils.hasText(userId) && StringUtils.hasText(rolesHeader)) {
            try {
                UUID userUuid = UUID.fromString(userId);

                List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userUuid, null, authorities);
                auth.setDetails(request);

                SecurityContextHolder.getContext().setAuthentication(auth);

                MDC.put("userId",  userId);
                MDC.put("traceId", userId);

                log.debug("Authenticated userId={} roles={}", userId, rolesHeader);

            } catch (IllegalArgumentException ex) {
                log.warn("Malformed {} header value: {}", HEADER_USER_ID, userId);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
            MDC.remove("traceId");
        }
    }
}
