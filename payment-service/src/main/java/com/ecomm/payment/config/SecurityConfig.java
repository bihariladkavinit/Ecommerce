package com.ecomm.payment.config;

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
 * Security configuration for payment-service.
 *
 * Auth model: API Gateway forwards X-User-Id / X-User-Roles after JWT validation.
 * GatewayAuthFilter builds the SecurityContext from those headers.
 *
 * Access rules:
 *   GET /payments/** - ROLE_ADMIN only (internal debugging endpoint)
 *   Actuator, OpenAPI - always public
 *
 * Payment-service is NOT routed through the Gateway; it is purely Kafka-driven.
 * The admin endpoint is accessible only within the Docker network.
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

                    .requestMatchers(
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html"
                    ).permitAll()

                    .requestMatchers("/actuator/**").permitAll()

                    .requestMatchers(HttpMethod.GET, "/payments/**").hasRole("ADMIN")

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
