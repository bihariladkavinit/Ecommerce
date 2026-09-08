package com.ecomm.cart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for cart-service.
 *
 * <p>Auth model: the API Gateway validates the JWT and forwards
 * {@code X-User-Id} / {@code X-User-Roles} headers. This service trusts
 * those headers via {@link GatewayAuthFilter} — no JWT library needed here.
 *
 * <p>Access rules:
 * <ul>
 *   <li>All {@code /cart/**} endpoints — authenticated users only (any role)</li>
 *   <li>Actuator, OpenAPI/Swagger — always public</li>
 * </ul>
 *
 * <p>All cart operations belong to the authenticated user identified by
 * {@code X-User-Id}. No admin-specific cart endpoints exist.
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

                    // ── OpenAPI / Swagger ──────────────────────────────────
                    .requestMatchers(
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html"
                    ).permitAll()

                    // ── Actuator ───────────────────────────────────────────
                    .requestMatchers("/actuator/**").permitAll()

                    // ── All cart endpoints require an authenticated user ────
                    .requestMatchers("/cart/**").authenticated()

                    // ── Fallback ───────────────────────────────────────────
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
