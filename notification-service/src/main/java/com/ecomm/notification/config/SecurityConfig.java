package com.ecomm.notification.config;

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
 * Security configuration for notification-service.
 *
 * <p>Auth model: the API Gateway validates the JWT and forwards
 * {@code X-User-Id} / {@code X-User-Roles} headers. This service trusts
 * those headers via {@link GatewayAuthFilter} — no JWT library needed.
 *
 * <p>Access rules:
 * <ul>
 *   <li>{@code GET /notifications/**} — ROLE_ADMIN only (debug history lookup)</li>
 *   <li>Actuator, OpenAPI/Swagger — always public</li>
 *   <li>Everything else — authenticated (defensive default)</li>
 * </ul>
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

                    // ── Admin-only notification history ────────────────────
                    .requestMatchers(HttpMethod.GET, "/notifications/**").hasRole("ADMIN")

                    // ── Defensive default ──────────────────────────────────
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
