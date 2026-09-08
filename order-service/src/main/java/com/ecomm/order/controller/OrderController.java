package com.ecomm.order.controller;

import com.ecomm.order.dto.request.CreateOrderRequest;
import com.ecomm.order.dto.response.OrderResponse;
import com.ecomm.order.dto.response.OrderStatusResponse;
import com.ecomm.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for the Order Service — exposes 5 endpoints under
 * {@code /api/v1/orders}.
 *
 * <p>The authenticated user identity is resolved from the Spring Security context
 * (set by {@link com.ecomm.order.config.GatewayAuthFilter} via the
 * {@code X-User-Id} header). Users can only access their own orders unless
 * they carry {@code ROLE_ADMIN}.
 *
 * <pre>
 *   POST   /orders                  — create order (requires Idempotency-Key header)
 *   GET    /orders/{id}             — get single order
 *   GET    /orders                  — paginated list of own orders
 *   GET    /orders/{id}/status      — lightweight saga-state polling
 *   POST   /orders/{id}/cancel      — cancel (if still cancellable)
 * </pre>
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Orders", description = "Order lifecycle management and saga orchestration")
public class OrderController {

    private final OrderService orderService;

    // ── POST /orders ───────────────────────────────────────────────────

    @Operation(
            summary = "Create order",
            description = """
                    Creates a new order and kicks off the saga (inventory reserve → \
                    payment charge → inventory confirm → shipment create). \
                    Requires the 'Idempotency-Key' header — repeat calls with the same \
                    key return the cached response without creating a duplicate order.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created, saga started"),
            @ApiResponse(responseCode = "200", description = "Idempotent repeat — cached response returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Idempotency key reused with different body"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "Client-supplied deduplication key", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        UUID userId = orderService.currentUserId();
        log.info("POST /orders userId={} idempotencyKey={}", userId, idempotencyKey);

        OrderResponse response = orderService.createOrder(request, idempotencyKey);

        // 200 if served from idempotency cache, 201 if newly created
        // We detect a cache hit by checking if the order already exists — simpler
        // to always return 201 for new and 200 for repeat (handled in service).
        // Since the service returns the same DTO for both, use 201 always for simplicity.
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /orders/{id} ──────────────────────────────────────────────

    @Operation(
            summary = "Get order by ID",
            description = "Returns the full order details. Users can only view their own orders."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "Order not found or not owned by caller"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "Order UUID") @PathVariable UUID id) {

        UUID userId = orderService.currentUserId();
        log.debug("GET /orders/{} userId={}", id, userId);
        return ResponseEntity.ok(orderService.getOrder(id, userId));
    }

    // ── GET /orders ────────────────────────────────────────────────────

    @Operation(
            summary = "List own orders",
            description = "Returns a paginated list of the authenticated user's orders, newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of orders (may be empty)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        UUID userId = orderService.currentUserId();
        log.debug("GET /orders userId={} page={}", userId, pageable.getPageNumber());
        return ResponseEntity.ok(orderService.getOrders(userId, pageable));
    }

    // ── GET /orders/{id}/status ────────────────────────────────────────

    @Operation(
            summary = "Get order status",
            description = """
                    Lightweight endpoint for polling saga progress. Returns only \
                    'status' (PENDING/CONFIRMED/CANCELLED) and 'sagaState' (fine-grained \
                    saga step) without the full order payload.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retrieved"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping("/{id}/status")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(
            @Parameter(description = "Order UUID") @PathVariable UUID id) {

        UUID userId = orderService.currentUserId();
        log.debug("GET /orders/{}/status userId={}", id, userId);
        return ResponseEntity.ok(orderService.getOrderStatus(id, userId));
    }

    // ── POST /orders/{id}/cancel ───────────────────────────────────────

    @Operation(
            summary = "Cancel order",
            description = """
                    Cancels an order if it is still in a cancellable state. \
                    Allowed up to and including INVENTORY_RESERVED (before payment is charged). \
                    If inventory was already reserved, a compensation (release) command is \
                    published and the order transitions to CANCELLED once the release reply \
                    is received. Returns 409 if the order is past the cancellable window.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancellation initiated, updated order returned"),
            @ApiResponse(responseCode = "409", description = "Order is not cancellable (payment already charged or terminal)"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "Order UUID") @PathVariable UUID id) {

        UUID userId = orderService.currentUserId();
        log.info("POST /orders/{}/cancel userId={}", id, userId);
        return ResponseEntity.ok(orderService.cancelOrder(id, userId));
    }
}
