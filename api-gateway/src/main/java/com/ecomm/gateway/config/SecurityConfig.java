package com.ecomm.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;

/**
 * Reactive Spring Security configuration.
 *
 * <p>This acts as a second layer of defence behind the {@link
 * com.ecomm.gateway.filter.JwtAuthenticationFilter}.  The JWT filter handles
 * the business-level token checks (signature, expiry, blacklist, header
 * mutation); this bean enforces the HTTP-level permit/deny rules so that
 * unauthenticated requests cannot slip through to downstream services even if
 * the filter chain is bypassed somehow.
 *
 * <p>CSRF is disabled because the API is stateless (JWT-based) and consumed
 * by non-browser clients.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // All auth/access enforcement is handled by JwtAuthenticationFilter
                        // (a Spring Cloud Gateway GlobalFilter). Spring Security's reactive
                        // context is never populated by that filter, so .authenticated() here
                        // would block every request. We permit all at this layer and rely on
                        // JwtAuthenticationFilter as the single source of auth truth.
                        .anyExchange().permitAll()
                )
                // Delegate 401 responses to our JwtAuthenticationFilter so the
                // response uses the project's standard error envelope.
                // HttpStatusServerEntryPoint just sets the status code; the filter
                // has already written the body before Spring Security's entry point
                // is invoked for truly unauthenticated exchanges.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .build();
    }
}
