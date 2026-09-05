package com.ecomm.user.controller;

import com.ecomm.user.dto.request.AddressRequest;
import com.ecomm.user.dto.request.UpdateUserRequest;
import com.ecomm.user.dto.response.AddressResponse;
import com.ecomm.user.dto.response.ErrorResponse;
import com.ecomm.user.dto.response.UserResponse;
import com.ecomm.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User profile and address endpoints.
 *
 * <p>All {@code /users/me/**} paths require a valid JWT (enforced by
 * {@link com.ecomm.user.config.SecurityConfig}).  The authenticated user's
 * UUID is read from the Spring {@link Authentication} principal set by
 * {@link com.ecomm.user.config.JwtFilter}.
 *
 * <p>{@code GET /users/{userId}} is permit-all (internal Feign only — not
 * exposed through the API Gateway).
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Profile management and address book")
public class UserController {

    private final UserService userService;

    // ── GET /users/me ─────────────────────────────────────────────────

    @Operation(
            summary = "Get current user profile",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "Profile returned",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(userService.getMe(userId));
    }

    // ── PUT /users/me ─────────────────────────────────────────────────

    @Operation(
            summary = "Update current user profile",
            description = "Updates firstName, lastName, and/or phone. Null fields are ignored.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "Profile updated",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateMe(userId, request));
    }

    // ── GET /users/me/addresses ───────────────────────────────────────

    @Operation(
            summary = "List addresses",
            description = "Returns all addresses for the current user, default address first.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "Address list returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddressResponse.class))))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me/addresses")
    public ResponseEntity<List<AddressResponse>> getAddresses(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(userService.getAddresses(userId));
    }

    // ── POST /users/me/addresses ──────────────────────────────────────

    @Operation(
            summary = "Add address",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "201", description = "Address created",
            content = @Content(schema = @Schema(implementation = AddressResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/me/addresses")
    public ResponseEntity<AddressResponse> addAddress(
            Authentication authentication,
            @Valid @RequestBody AddressRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.addAddress(userId, request));
    }

    // ── PUT /users/me/addresses/{id} ──────────────────────────────────

    @Operation(
            summary = "Update address",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "Address updated",
            content = @Content(schema = @Schema(implementation = AddressResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Address not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/me/addresses/{id}")
    public ResponseEntity<AddressResponse> updateAddress(
            Authentication authentication,
            @Parameter(description = "Address UUID") @PathVariable UUID id,
            @Valid @RequestBody AddressRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateAddress(userId, id, request));
    }

    // ── DELETE /users/me/addresses/{id} ──────────────────────────────

    @Operation(
            summary = "Delete address",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "204", description = "Address deleted")
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Address not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/me/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(
            Authentication authentication,
            @Parameter(description = "Address UUID") @PathVariable UUID id) {
        UUID userId = (UUID) authentication.getPrincipal();
        userService.deleteAddress(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── GET /users/{userId} — internal Feign endpoint ─────────────────

    @Operation(
            summary = "Get user by ID (internal)",
            description = "Used by Order Service via Feign. Not routed through the API Gateway."
    )
    @ApiResponse(responseCode = "200", description = "User found",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }
}
