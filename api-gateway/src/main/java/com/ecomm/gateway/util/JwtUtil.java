package com.ecomm.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Utility for parsing and validating HS256 JWTs.
 *
 * <p>The secret is read from the {@code jwt.secret} property (bound from the
 * {@code JWT_SECRET} environment variable via Spring's relaxed binding).
 *
 * <p>This class is intentionally free of reactive types so it can be unit-tested
 * without a Spring context and reused synchronously inside the WebFlux filter chain.
 */
@Component
public class JwtUtil {

    private final SecretKey signingKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // JJWT 0.12.x: Keys.hmacShaKeyFor requires the raw bytes of the secret
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses the token and returns all claims.
     *
     * @throws JwtException if the token is malformed, expired, or the signature is invalid
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns the {@code sub} claim (user ID) from a valid token.
     */
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Returns the {@code roles} claim as a comma-separated string.
     * Falls back to an empty string when the claim is absent.
     */
    public String extractRoles(String token) {
        Object roles = extractAllClaims(token).get("roles");
        return roles != null ? roles.toString() : "";
    }

    /**
     * Returns the JWT ID ({@code jti}) claim used for blacklist lookups.
     */
    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    /**
     * Returns {@code true} if the token has a valid signature and is not expired.
     * Never throws — intended for use in filter guard conditions.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
