package com.ecomm.product.config;

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
 * Security configuration for product-service.
 *
 * <p>Auth model: the API Gateway validates the JWT and forwards
 * {@code X-User-Id} / {@code X-User-Roles} headers. This service
 * trusts those headers via {@link GatewayAuthFilter} — no JWT library needed.
 *
 * <p>Access rules:
 * <ul>
 *   <li>{@code GET /products/**}, {@code GET /categories/**} — public (browsing)</li>
 *   <li>{@code POST/PUT/DELETE /products/**}, {@code POST /categories} — ROLE_ADMIN only</li>
 *   <li>Actuator, OpenAPI — always public</li>
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

                    // ── Public read access ─────────────────────────────────
                    .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/categories/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/categories").permitAll()

                    // ── Admin-only writes ──────────────────────────────────
                    .requestMatchers(HttpMethod.POST,   "/products/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT,    "/products/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/products/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST,   "/categories/**").hasRole("ADMIN")

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
