package com.ecomm.user.controller;

import com.ecomm.user.dto.request.LoginRequest;
import com.ecomm.user.dto.request.RefreshRequest;
import com.ecomm.user.dto.request.RegisterRequest;
import com.ecomm.user.dto.response.ErrorResponse;
import com.ecomm.user.dto.response.TokenResponse;
import com.ecomm.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints — no JWT required.
 *
 * <p>All paths are under {@code /auth/**} which is permit-all in
 * {@link com.ecomm.user.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh, and logout")
public class AuthController {

    private final AuthService authService;

    // ── POST /auth/register ───────────────────────────────────────────

    @Operation(
            summary = "Register a new user",
            description = "Creates an account and returns an access + refresh token pair."
    )
    @ApiResponse(responseCode = "201", description = "User registered successfully",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse tokens = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tokens);
    }

    // ── POST /auth/login ──────────────────────────────────────────────

    @Operation(
            summary = "Login",
            description = "Validates credentials and returns an access + refresh token pair."
    )
    @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ── POST /auth/refresh ────────────────────────────────────────────

    @Operation(
            summary = "Refresh access token",
            description = "Exchanges a valid refresh token for a new token pair (rotation)."
    )
    @ApiResponse(responseCode = "200", description = "Token refreshed",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    // ── POST /auth/logout ─────────────────────────────────────────────

    @Operation(
            summary = "Logout",
            description = "Blacklists the current access token and revokes all refresh tokens."
    )
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            @RequestHeader("Authorization") String authHeader) {

        String token = extractBearerToken(authHeader);
        authService.logout(authentication, token);
        return ResponseEntity.noContent().build();
    }

    // ── helper ────────────────────────────────────────────────────────

    private String extractBearerToken(String header) {
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}
