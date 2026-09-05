package com.ecomm.user.service;

import com.ecomm.user.dto.request.LoginRequest;
import com.ecomm.user.dto.request.RefreshRequest;
import com.ecomm.user.dto.request.RegisterRequest;
import com.ecomm.user.dto.response.TokenResponse;
import com.ecomm.user.entity.RefreshToken;
import com.ecomm.user.entity.Role;
import com.ecomm.user.entity.User;
import com.ecomm.user.exception.ConflictException;
import com.ecomm.user.exception.ResourceNotFoundException;
import com.ecomm.user.exception.UnauthorizedException;
import com.ecomm.user.repository.RefreshTokenRepository;
import com.ecomm.user.repository.RoleRepository;
import com.ecomm.user.repository.UserRepository;
import com.ecomm.user.util.HashUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles all authentication flows:
 * register, login, token refresh, and logout.
 *
 * <p>Every method that writes to the DB is wrapped in a single transaction so
 * the user/token rows and any compensating rollback stay atomic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String ROLE_USER    = "ROLE_USER";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final UserRepository          userRepository;
    private final RoleRepository          roleRepository;
    private final RefreshTokenRepository  refreshTokenRepository;
    private final PasswordEncoder         passwordEncoder;
    private final JwtService              jwtService;
    private final RedisTemplate<String, String> redisTemplate;

    // ── Register ──────────────────────────────────────────────────────

    /**
     * Creates a new user account and returns a fresh token pair.
     *
     * @throws ConflictException if the email is already registered
     */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }

        // Resolve the default ROLE_USER from the seeded roles table
        Role userRole = roleRepository.findByName(ROLE_USER)
                .orElseThrow(() -> new ResourceNotFoundException("Role", ROLE_USER));

        User user = User.builder()
                .email(request.getEmail().toLowerCase().strip())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().strip())
                .lastName(request.getLastName().strip())
                .isActive(true)
                .build();
        user.addRole(userRole);

        user = userRepository.save(user);
        log.info("Registered new user id={} email={}", user.getId(), user.getEmail());

        return issueTokenPair(user);
    }

    // ── Login ─────────────────────────────────────────────────────────

    /**
     * Authenticates credentials and returns a fresh token pair.
     *
     * @throws UnauthorizedException on bad credentials or inactive account
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().strip())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email={}", request.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("Successful login for user id={}", user.getId());
        return issueTokenPair(user);
    }

    // ── Refresh ───────────────────────────────────────────────────────

    /**
     * Exchanges a valid refresh token for a new token pair (rotation).
     * The old refresh token is revoked atomically in the same transaction.
     *
     * @throws UnauthorizedException if the token is unknown, revoked, or expired
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String tokenHash = HashUtil.sha256Hex(request.getRefreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!stored.isValid()) {
            // Revoke all tokens for this user as a safety measure —
            // a compromised / replayed token is a signal of potential theft.
            refreshTokenRepository.revokeAllByUserId(stored.getUserId());
            log.warn("Replayed or expired refresh token for userId={}", stored.getUserId());
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        User user = userRepository.findByIdWithRoles(stored.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", stored.getUserId()));

        // Rotate: revoke old, issue new
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokenPair(user);
    }

    // ── Logout ────────────────────────────────────────────────────────

    /**
     * Invalidates the current session:
     * <ol>
     *   <li>Adds the access token's {@code jti} to the Redis blacklist with
     *       a TTL equal to the token's remaining lifetime.</li>
     *   <li>Revokes all refresh tokens for the user in the DB.</li>
     * </ol>
     *
     * @param authentication the current Spring Security authentication
     *                       (populated by {@link com.ecomm.user.config.JwtFilter})
     * @param rawAccessToken  the raw JWT string from the Authorization header
     */
    @Transactional
    public void logout(Authentication authentication, String rawAccessToken) {
        Claims claims = jwtService.validateAndExtractClaims(rawAccessToken);
        String jti    = jwtService.extractJti(claims);
        long   ttlMs  = jwtService.getRemainingTtlMs(claims);
        UUID   userId = (UUID) authentication.getPrincipal();

        // Blacklist the JTI so the access token is rejected on subsequent requests
        if (ttlMs > 0) {
            redisTemplate.opsForValue()
                    .set(BLACKLIST_PREFIX + jti, "1", ttlMs, TimeUnit.MILLISECONDS);
        }

        // Revoke all refresh tokens — logs out of every device
        refreshTokenRepository.revokeAllByUserId(userId);

        log.info("Logout: userId={} jti={} blacklisted for {}ms", userId, jti, ttlMs);
    }

    // ── Internal helpers ──────────────────────────────────────────────

    /**
     * Generates an access + refresh token pair and persists the refresh token hash.
     * Called by register, login, and refresh flows.
     */
    private TokenResponse issueTokenPair(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        RefreshToken tokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(HashUtil.sha256Hex(refreshToken))
                .expiresAt(OffsetDateTime.now().plusNanos(
                        jwtService.getRefreshTokenExpiryMs() * 1_000_000L))
                .revoked(false)
                .build();

        refreshTokenRepository.save(tokenEntity);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpiryMs())
                .build();
    }
}
