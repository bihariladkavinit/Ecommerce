package com.ecomm.order.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Global Feign interceptor that forwards the trusted auth headers
 * ({@code X-User-Id}, {@code X-User-Roles}) on every outbound Feign call.
 *
 * <p>This allows downstream services (user-service in this case) to build
 * their own security context. Headers are only forwarded when an active
 * HTTP request is in scope — Feign calls made outside a request context
 * (e.g. scheduler threads) will omit the headers gracefully.
 */
@Configuration
@Slf4j
public class FeignHeaderInterceptor {

    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Bean
    public RequestInterceptor forwardAuthHeaders() {
        return (RequestTemplate template) -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attrs == null) {
                return; // no active HTTP request — skip header forwarding
            }

            HttpServletRequest request = attrs.getRequest();

            String userId = request.getHeader(HEADER_USER_ID);
            String roles  = request.getHeader(HEADER_USER_ROLES);

            if (StringUtils.hasText(userId)) {
                template.header(HEADER_USER_ID, userId);
            }
            if (StringUtils.hasText(roles)) {
                template.header(HEADER_USER_ROLES, roles);
            }

            log.debug("Forwarding auth headers to downstream Feign call: userId={}", userId);
        };
    }
}
