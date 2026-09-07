package com.ecomm.inventory.config;

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
 * Security configuration for inventory-service.
 *
 * <p>Auth model: the API Gateway validates the JWT and forwards
 * {@code X-User-Id} / {@code X-User-Roles} headers. This service trusts
 * those headers via {@link GatewayAuthFilter} — no JWT library needed here.
 *
 * <p>Access rules:
 * <ul>
 *   <li>{@code GET /inventory/{id}} — authenticated users (User or Admin)</li>
 *   <li>{@code GET /inventory/{id}/availability} — permit-all (internal Feign from Cart, no JWT forwarded)</li>
 *   <li>{@code PUT /inventory/{id}} — ROLE_ADMIN only (restock)</li>
 *   <li>Actuator, OpenAPI/Swagger — always public</li>
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

                    // ── Internal Feign: availability check (Cart → Inventory)
                    // Cart Service calls this from within the Docker network
                    // without forwarding a JWT, so it must be permit-all.
                    .requestMatchers(HttpMethod.GET, "/inventory/*/availability").permitAll()

                    // ── Admin-only restock ────────────────────────────────
                    .requestMatchers(HttpMethod.PUT, "/inventory/**").hasRole("ADMIN")

                    // ── Authenticated read ─────────────────────────────────
                    .requestMatchers(HttpMethod.GET, "/inventory/**").authenticated()

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
