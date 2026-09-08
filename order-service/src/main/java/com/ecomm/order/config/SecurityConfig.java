package com.ecomm.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for order-service.
 *
 * Auth model: the API Gateway validates the JWT and forwards X-User-Id and
 * X-User-Roles as trusted internal headers. GatewayAuthFilter turns those into
 * a Spring Security Authentication so @PreAuthorize checks work normally.
 *
 * Access rules:
 *   POST /orders                - internal Feign from Cart Service; forwarded headers required
 *   GET /orders/**, POST /orders/{id}/cancel - any authenticated user; service enforces ownership
 *   Actuator, OpenAPI/Swagger   - always public
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm ->
                    sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                    // OpenAPI / Swagger
                    .requestMatchers(
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html"
                    ).permitAll()

                    // Actuator
                    .requestMatchers("/actuator/**").permitAll()

                    // Order endpoints
                    .requestMatchers(HttpMethod.POST, "/orders").authenticated()
                    .requestMatchers(HttpMethod.GET,  "/orders/**").authenticated()
                    .requestMatchers(HttpMethod.POST, "/orders/*/cancel").authenticated()

                    // Fallback
                    .anyRequest().authenticated()
            )
            .addFilterBefore(gatewayAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public GatewayAuthFilter gatewayAuthFilter() {
        return new GatewayAuthFilter();
    }
}
