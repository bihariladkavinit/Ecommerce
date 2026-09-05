package com.ecomm.user.service;

import com.ecomm.user.config.JwtProperties;
import com.ecomm.user.entity.User;
import com.ecomm.user.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Encapsulates all JWT logic:
 * <ul>
 *   <li>Access token generation (HS256, 15 min)</li>
 *   <li>Refresh token generation (opaque, 7 days)</li>
 *   <li>Token validation and claims extraction</li>
 * </ul>
 *
 * <p>Claims carried in the access token:
 * <ul>
 *   <li>{@code sub}   — userId (UUID string)</li>
 *   <li>{@code roles} — comma-separated list, e.g. "ROLE_USER"</li>
 *   <li>{@code jti}   — UUID, used for blacklist on logout</li>
 *   <li>{@code exp}   — expiry timestamp</li>
 *   <li>{@code iat}   — issued-at timestamp</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private static final String CLAIM_ROLES = "roles";

    private final JwtProperties jwtProperties;

    /** Derived once from the configured secret on startup. */
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        // Keys.hmacShaKeyFor pads / truncates to the correct HMAC-SHA key size
        signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtService initialised — access TTL={}ms  refresh TTL={}ms",
                jwtProperties.getAccessTokenExpiryMs(),
                jwtProperties.getRefreshTokenExpiryMs());
    }

    // ── Access token ──────────────────────────────────────────────────

    /**
     * Generates a signed HS256 access token for the given user.
     *
     * @param user the authenticated / registered user
     * @return compact JWT string
     */
    public String generateAccessToken(User user) {
        long now = System.currentTimeMillis();
        String roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_ROLES, roles)
                .id(UUID.randomUUID().toString())          // jti — unique per token
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.getAccessTokenExpiryMs()))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates a compact JWT string and returns the parsed {@link Claims}.
     *
     * @throws UnauthorizedException if the token is missing, malformed, expired,
     *                               or has an invalid signature
     */
    public Claims validateAndExtractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
            throw new UnauthorizedException("Access token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT invalid: {}", ex.getMessage());
            throw new UnauthorizedException("Invalid access token");
        }
    }

    // ── Claims extraction helpers ─────────────────────────────────────

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String extractJti(Claims claims) {
        return claims.getId();
    }

    /**
     * Returns the roles as a {@link Set} of strings.
     * The claim is stored as a comma-separated string to keep the JWT compact.
     */
    public Set<String> extractRoles(Claims claims) {
        String rolesStr = claims.get(CLAIM_ROLES, String.class);
        if (rolesStr == null || rolesStr.isBlank()) return Set.of();
        return Set.of(rolesStr.split(","));
    }

    /**
     * Returns the remaining lifetime (in ms) of a token, given its {@link Claims}.
     * Useful when writing the Redis blacklist TTL on logout.
     */
    public long getRemainingTtlMs(Claims claims) {
        long expMs = claims.getExpiration().getTime();
        long remaining = expMs - System.currentTimeMillis();
        return Math.max(remaining, 0L);
    }

    // ── Refresh token ─────────────────────────────────────────────────

    /**
     * Generates a cryptographically secure opaque refresh token.
     * The raw value is returned to the caller once; only its SHA-256
     * hash is persisted in the database.
     *
     * @return 64-character Base64url-encoded random string
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public long getRefreshTokenExpiryMs() {
        return jwtProperties.getRefreshTokenExpiryMs();
    }

    public long getAccessTokenExpiryMs() {
        return jwtProperties.getAccessTokenExpiryMs();
    }
}
