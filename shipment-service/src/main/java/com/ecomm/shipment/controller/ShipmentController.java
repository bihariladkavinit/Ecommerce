package com.ecomm.shipment.controller;

import com.ecomm.shipment.dto.request.ShipmentStatusUpdateRequest;
import com.ecomm.shipment.dto.response.ErrorResponse;
import com.ecomm.shipment.dto.response.ShipmentResponse;
import com.ecomm.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for shipment management.
 *
 * <p>All paths are prefixed with the context-path {@code /api/v1} from
 * {@code application.yml}, so the effective Gateway-facing paths are:
 * <ul>
 *   <li>{@code GET  /api/v1/shipments/{orderId}} — authenticated user lookup</li>
 *   <li>{@code GET  /api/v1/shipments/track/{trackingNumber}} — public tracking</li>
 *   <li>{@code PUT  /api/v1/shipments/{orderId}/status} — Admin only</li>
 * </ul>
 *
 * <p>Note: the {@code /track/{trackingNumber}} path is mapped before
 * {@code /{orderId}} to ensure Spring MVC routes the literal segment "track"
 * correctly before trying to parse it as a UUID.
 */
@RestController
@RequestMapping("/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Shipment tracking and admin status management")
public class ShipmentController {

    private final ShipmentService shipmentService;

    // ── GET /shipments/{orderId} ──────────────────────────────────────

    @Operation(
            summary = "Get shipment by order ID",
            description = "Returns the shipment associated with the given order. " +
                    "Requires authentication.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shipment found",
                    content = @Content(schema = @Schema(implementation = ShipmentResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No shipment for this order",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<ShipmentResponse> getByOrderId(
            @Parameter(description = "Order UUID", required = true)
            @PathVariable UUID orderId) {

        return ResponseEntity.ok(shipmentService.getByOrderId(orderId));
    }

    // ── GET /shipments/track/{trackingNumber} ─────────────────────────

    @Operation(
            summary = "Track a shipment by tracking number",
            description = "Public endpoint — no authentication required. " +
                    "Returns the shipment matching the given tracking number " +
                    "(e.g. TRK-A1B2C3D4).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shipment found",
                    content = @Content(schema = @Schema(implementation = ShipmentResponse.class))),
            @ApiResponse(responseCode = "404", description = "No shipment with that tracking number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<ShipmentResponse> getByTrackingNumber(
            @Parameter(description = "Tracking number, e.g. TRK-A1B2C3D4", required = true)
            @PathVariable String trackingNumber) {

        return ResponseEntity.ok(shipmentService.getByTrackingNumber(trackingNumber));
    }

    // ── PUT /shipments/{orderId}/status ───────────────────────────────

    @Operation(
            summary = "Update shipment status (Admin)",
            description = "Advances the shipment to the given status. " +
                    "Valid transitions: CREATED → DISPATCHED → DELIVERED. " +
                    "Both CREATED and DISPATCHED can also move to CANCELLED. " +
                    "DELIVERED and CANCELLED are terminal states. " +
                    "Requires ROLE_ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(schema = @Schema(implementation = ShipmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (null status)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Insufficient role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No shipment for this order",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid status transition",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShipmentResponse> updateStatus(
            @Parameter(description = "Order UUID", required = true)
            @PathVariable UUID orderId,
            @Valid @RequestBody ShipmentStatusUpdateRequest request) {

        return ResponseEntity.ok(shipmentService.updateStatus(orderId, request));
    }
}
