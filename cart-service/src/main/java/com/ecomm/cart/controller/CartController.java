package com.ecomm.cart.controller;

import com.ecomm.cart.dto.client.OrderResponse;
import com.ecomm.cart.dto.request.AddItemRequest;
import com.ecomm.cart.dto.request.CheckoutRequest;
import com.ecomm.cart.dto.request.UpdateItemRequest;
import com.ecomm.cart.dto.response.CartResponse;
import com.ecomm.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller exposing the six cart endpoints under {@code /api/v1/cart}.
 *
 * <p>The user identity is resolved from the Spring Security context (set by
 * {@link com.ecomm.cart.config.GatewayAuthFilter}) rather than path/query params —
 * a user can only ever access their own cart.
 *
 * <p>Endpoint summary:
 * <pre>
 *   GET    /cart                      — get enriched cart
 *   POST   /cart/items                — add item (merge qty if already present)
 *   PUT    /cart/items/{productId}    — set item qty (0 = remove)
 *   DELETE /cart/items/{productId}    — remove single item
 *   DELETE /cart                      — clear entire cart
 *   POST   /cart/checkout             — validate + submit order + clear cart
 * </pre>
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart", description = "Shopping cart management and checkout")
public class CartController {

    private final CartService cartService;

    // ── GET /cart ──────────────────────────────────────────────────────

    @Operation(
            summary = "Get cart",
            description = "Returns the authenticated user's cart enriched with live product name and price."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart retrieved (may be empty)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @GetMapping
    public ResponseEntity<CartResponse> getCart() {
        UUID userId = cartService.currentUserId();
        log.debug("GET /cart userId={}", userId);
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    // ── POST /cart/items ───────────────────────────────────────────────

    @Operation(
            summary = "Add item to cart",
            description = "Adds a product to the cart. If the product is already present its quantity is merged."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item added, updated cart returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request or inactive product"),
            @ApiResponse(responseCode = "404", description = "Product not found or temporarily unavailable"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @Valid @RequestBody AddItemRequest request) {

        UUID userId = cartService.currentUserId();
        log.debug("POST /cart/items userId={} productId={} qty={}",
                userId, request.getProductId(), request.getQty());
        return ResponseEntity.ok(cartService.addItem(userId, request));
    }

    // ── PUT /cart/items/{productId} ────────────────────────────────────

    @Operation(
            summary = "Update item quantity",
            description = "Sets the quantity of an existing cart item. Sending qty=0 removes the item."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item updated, updated cart returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Product not in cart"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItem(
            @Parameter(description = "UUID of the product to update")
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateItemRequest request) {

        UUID userId = cartService.currentUserId();
        log.debug("PUT /cart/items/{} userId={} qty={}", productId, userId, request.getQty());
        return ResponseEntity.ok(cartService.updateItem(userId, productId, request));
    }

    // ── DELETE /cart/items/{productId} ─────────────────────────────────

    @Operation(
            summary = "Remove item from cart",
            description = "Removes a single product from the cart. Idempotent — no error if the product is not present."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item removed, updated cart returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(
            @Parameter(description = "UUID of the product to remove")
            @PathVariable UUID productId) {

        UUID userId = cartService.currentUserId();
        log.debug("DELETE /cart/items/{} userId={}", productId, userId);
        return ResponseEntity.ok(cartService.removeItem(userId, productId));
    }

    // ── DELETE /cart ───────────────────────────────────────────────────

    @Operation(
            summary = "Clear cart",
            description = "Removes all items from the cart. Idempotent — safe to call on an already-empty cart."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cart cleared"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        UUID userId = cartService.currentUserId();
        log.debug("DELETE /cart userId={}", userId);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    // ── POST /cart/checkout ────────────────────────────────────────────

    @Operation(
            summary = "Checkout",
            description = """
                    Validates cart items against product-service (price snapshot) and \
                    inventory-service (availability), then creates an order via order-service. \
                    The cart is cleared on success. Requires the 'Idempotency-Key' header to \
                    prevent duplicate orders on retry.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created, cart cleared"),
            @ApiResponse(responseCode = "400", description = "Cart is empty or request invalid"),
            @ApiResponse(responseCode = "409", description = "One or more items unavailable, or order-service unavailable"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
    })
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @Parameter(
                    description = "Client-supplied deduplication key. Use the same key on retries " +
                                  "to avoid creating duplicate orders.",
                    required = true,
                    example = "a1b2c3d4-checkout-attempt-1"
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {

        UUID userId = cartService.currentUserId();
        log.info("POST /cart/checkout userId={} idempotencyKey={}", userId, idempotencyKey);

        OrderResponse order = cartService.checkout(userId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
