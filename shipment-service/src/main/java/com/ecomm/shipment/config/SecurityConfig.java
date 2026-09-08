package com.ecomm.shipment.config;

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
 * Security configuration for shipment-service.
 *
 * <p>Auth model: the API Gateway validates the JWT and forwards
 * {@code X-User-Id} / {@code X-User-Roles} headers. This service trusts
 * those headers via {@link GatewayAuthFilter} — no JWT library needed.
 *
 * <p>Access rules:
 * <ul>
 *   <li>{@code GET /shipments/track/**} — public (anyone can track a package
 *       with a tracking number, per api-contracts.md)</li>
 *   <li>{@code GET /shipments/**} — authenticated users (own order lookup)</li>
 *   <li>{@code PUT /shipments/**} — ROLE_ADMIN only (status update)</li>
 *   <li>Actuator, OpenAPI/Swagger — always public</li>
 * </ul>
 *
 * <p>Note: the {@code /track/**} rule must be declared <em>before</em> the
 * generic {@code /shipments/**} authenticated rule so Spring Security
 * evaluates the more-specific path first.
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

                    // ── Public tracking — no auth required ────────────────
                    // Must be declared before the general /shipments/** rule.
                    .requestMatchers(HttpMethod.GET, "/shipments/track/**").permitAll()

                    // ── Admin-only status updates ──────────────────────────
                    .requestMatchers(HttpMethod.PUT, "/shipments/**").hasRole("ADMIN")

                    // ── Authenticated shipment lookup ──────────────────────
                    .requestMatchers(HttpMethod.GET, "/shipments/**").authenticated()

                    // ── Everything else requires authentication ────────────
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
