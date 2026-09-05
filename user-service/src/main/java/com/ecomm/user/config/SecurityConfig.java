package com.ecomm.user.config;

import com.ecomm.user.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Stateless (no HTTP session) — all state lives in the JWT / Redis.</li>
 *   <li>CSRF disabled — not applicable for a token-based REST API.</li>
 *   <li>{@code /auth/**} and actuator health are public; everything else requires auth.</li>
 *   <li>{@code GET /users/{userId}} is permitted for service-to-service Feign calls
 *       originating inside the Docker network (no public route through the gateway).</li>
 *   <li>{@link JwtFilter} runs before Spring's default
 *       {@link UsernamePasswordAuthenticationFilter}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final RedisTemplate<String, String> redisTemplate;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm ->
                    sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // ── Public auth endpoints ──────────────────────────────
                    .requestMatchers("/auth/**").permitAll()

                    // ── Actuator ───────────────────────────────────────────
                    .requestMatchers("/actuator/**").permitAll()

                    // ── OpenAPI / Swagger ──────────────────────────────────
                    .requestMatchers(
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html"
                    ).permitAll()

                    // ── Internal Feign: GET /users/{userId} ────────────────
                    // Reachable only within the Docker network — not exposed
                    // via the API Gateway, so no JWT is forwarded by callers.
                    .requestMatchers(HttpMethod.GET, "/users/{userId}").permitAll()

                    // ── Everything else requires a valid JWT ───────────────
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtFilter jwtFilter() {
        return new JwtFilter(jwtService, redisTemplate);
    }

    /**
     * BCrypt with strength 12 — a good balance between security and latency
     * for a user-facing login endpoint.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
