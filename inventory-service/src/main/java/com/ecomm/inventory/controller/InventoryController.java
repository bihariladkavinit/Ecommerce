package com.ecomm.inventory.controller;

import com.ecomm.inventory.dto.request.StockUpdateRequest;
import com.ecomm.inventory.dto.response.AvailabilityResponse;
import com.ecomm.inventory.dto.response.ErrorResponse;
import com.ecomm.inventory.dto.response.StockResponse;
import com.ecomm.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for inventory stock management.
 *
 * <p>All paths are prefixed with the context-path {@code /api/v1} from
 * {@code application.yml}, so the effective Gateway-facing paths are:
 * <ul>
 *   <li>{@code GET  /api/v1/inventory/{productId}}</li>
 *   <li>{@code GET  /api/v1/inventory/{productId}/availability?qty=N}</li>
 *   <li>{@code PUT  /api/v1/inventory/{productId}} (Admin only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Validated
@Tag(name = "Inventory", description = "Stock level management and availability checks")
public class InventoryController {

    private final InventoryService inventoryService;

    // ── GET /inventory/{productId} ────────────────────────────────────

    @Operation(
            summary = "Get stock levels",
            description = "Returns current available and reserved quantities for a product.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock levels returned",
                    content = @Content(schema = @Schema(implementation = StockResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No stock record for this product",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getStock(
            @Parameter(description = "Product UUID", required = true)
            @PathVariable UUID productId) {

        return ResponseEntity.ok(inventoryService.getStock(productId));
    }

    // ── GET /inventory/{productId}/availability ───────────────────────

    @Operation(
            summary = "Check stock availability",
            description = "Returns whether the requested quantity is currently available. " +
                    "Used internally by Cart Service via Feign — no auth header required " +
                    "from within the Docker network.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability result",
                    content = @Content(schema = @Schema(implementation = AvailabilityResponse.class)))
    })
    @GetMapping("/{productId}/availability")
    public ResponseEntity<AvailabilityResponse> checkAvailability(
            @Parameter(description = "Product UUID", required = true)
            @PathVariable UUID productId,
            @Parameter(description = "Requested quantity (must be >= 1)", required = true)
            @RequestParam @Min(value = 1, message = "qty must be at least 1") int qty) {

        return ResponseEntity.ok(inventoryService.checkAvailability(productId, qty));
    }

    // ── PUT /inventory/{productId} ────────────────────────────────────

    @Operation(
            summary = "Restock a product (Admin)",
            description = "Adds the given quantity to the product's available stock. " +
                    "Creates the stock record if it does not yet exist. " +
                    "Requires ROLE_ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock updated",
                    content = @Content(schema = @Schema(implementation = StockResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Insufficient role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Concurrent update conflict after retries",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockResponse> restock(
            @Parameter(description = "Product UUID", required = true)
            @PathVariable UUID productId,
            @Valid @RequestBody StockUpdateRequest request) {

        return ResponseEntity.ok(inventoryService.restock(productId, request));
    }
}
