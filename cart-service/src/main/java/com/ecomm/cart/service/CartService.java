package com.ecomm.cart.service;

import com.ecomm.cart.client.InventoryClient;
import com.ecomm.cart.client.OrderClient;
import com.ecomm.cart.client.ProductClient;
import com.ecomm.cart.dto.client.*;
import com.ecomm.cart.dto.request.AddItemRequest;
import com.ecomm.cart.dto.request.CheckoutRequest;
import com.ecomm.cart.dto.request.UpdateItemRequest;
import com.ecomm.cart.dto.response.CartItemResponse;
import com.ecomm.cart.dto.response.CartResponse;
import com.ecomm.cart.exception.BadRequestException;
import com.ecomm.cart.exception.CartEmptyException;
import com.ecomm.cart.exception.ProductUnavailableException;
import com.ecomm.cart.exception.ResourceNotFoundException;
import com.ecomm.cart.repository.CartRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core business logic for cart management and checkout.
 *
 * <h2>Data flow</h2>
 * <pre>
 *   Redis hash (cart:{userId}) — source of truth for item quantities.
 *   ProductClient (Feign)      — enriches items with name + price.
 *   InventoryClient (Feign)    — availability gate at checkout.
 *   OrderClient (Feign)        — creates the order and clears the cart.
 * </pre>
 *
 * <h2>Fallback behaviour</h2>
 * <ul>
 *   <li>ProductClient returns {@code null} when its circuit is open.
 *       CRUD reads degrade gracefully (placeholder name/zero price).
 *       Add-item and checkout reject the call so invalid data never enters
 *       the cart or the order.</li>
 *   <li>InventoryClient returns {@code available=false} when its circuit is
 *       open — checkout blocks, which is the safe default.</li>
 *   <li>OrderClient throws {@link com.ecomm.cart.exception.ConflictException}
 *       when its circuit is open — checkout fails loudly; the cart is preserved
 *       so the user can retry.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRedisRepository cartRepo;
    private final ProductClient       productClient;
    private final InventoryClient     inventoryClient;
    private final OrderClient         orderClient;

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Extracts the authenticated user's UUID from the Spring Security context.
     * {@link com.ecomm.cart.config.GatewayAuthFilter} sets this as the principal.
     */
    public UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        // Should never happen if the security filter is correctly configured
        throw new IllegalStateException("Principal is not a UUID: " + principal);
    }

    // ── Read ──────────────────────────────────────────────────────────

    /**
     * Returns the authenticated user's cart, enriched with live product data.
     *
     * <p>If product-service is unavailable (fallback returns null), the item is
     * still included with a placeholder name and zero price so the cart remains
     * readable — a degraded-but-functional response is better than a 500.
     *
     * @param userId the authenticated user
     * @return enriched cart; empty cart if no items stored
     */
    public CartResponse getCart(UUID userId) {
        Map<Object, Object> raw = cartRepo.findAllItems(userId);

        List<CartItemResponse> items = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            UUID productId = UUID.fromString((String) entry.getKey());
            int  qty       = parseQty((String) entry.getValue(), productId);

            CartItemResponse item = enrichItem(productId, qty);
            items.add(item);
        }

        BigDecimal total = items.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug("getCart userId={} itemCount={} total={}", userId, items.size(), total);
        return CartResponse.builder()
                .userId(userId)
                .items(items)
                .total(total)
                .build();
    }

    // ── Add item ──────────────────────────────────────────────────────

    /**
     * Adds a product to the cart, merging qty if it already exists.
     *
     * <p>Validates that:
     * <ol>
     *   <li>The product exists in product-service (Feign call succeeds).</li>
     *   <li>The product is active (not soft-deleted).</li>
     * </ol>
     *
     * @param userId  the authenticated user
     * @param request contains productId and qty to add
     * @return updated enriched cart
     * @throws ResourceNotFoundException if product-service cannot be reached or product not found
     * @throws BadRequestException       if the product is inactive
     */
    public CartResponse addItem(UUID userId, AddItemRequest request) {
        UUID productId = request.getProductId();

        ProductResponse product = productClient.getProduct(productId);
        if (product == null) {
            throw new ResourceNotFoundException(
                    "Product " + productId + " is currently unavailable — please retry");
        }
        if (!product.isActive()) {
            throw new BadRequestException("Product " + productId + " is no longer available");
        }

        // Merge qty: if already in cart, add to existing quantity
        int existing = cartRepo.getItem(userId, productId).orElse(0);
        int newQty   = existing + request.getQty();
        cartRepo.setItem(userId, productId, newQty);

        log.info("Item added/merged userId={} productId={} prevQty={} newQty={}",
                userId, productId, existing, newQty);
        return getCart(userId);
    }

    // ── Update item ───────────────────────────────────────────────────

    /**
     * Sets the quantity of a cart item to the supplied value.
     *
     * <p>A qty of 0 delegates to {@link #removeItem} so callers can use either
     * endpoint to zero out an item.
     *
     * @param userId    the authenticated user
     * @param productId the product to update
     * @param request   contains the new qty
     * @return updated enriched cart
     * @throws ResourceNotFoundException if the product is not currently in the cart
     */
    public CartResponse updateItem(UUID userId, UUID productId, UpdateItemRequest request) {
        if (cartRepo.getItem(userId, productId).isEmpty()) {
            throw new ResourceNotFoundException(
                    "Product " + productId + " is not in your cart");
        }

        if (request.getQty() == 0) {
            return removeItem(userId, productId);
        }

        cartRepo.setItem(userId, productId, request.getQty());
        log.info("Item updated userId={} productId={} qty={}", userId, productId, request.getQty());
        return getCart(userId);
    }

    // ── Remove item ───────────────────────────────────────────────────

    /**
     * Removes a single product from the cart.
     *
     * <p>No-op if the product is not in the cart (idempotent DELETE).
     *
     * @param userId    the authenticated user
     * @param productId the product to remove
     * @return updated enriched cart (may be empty)
     */
    public CartResponse removeItem(UUID userId, UUID productId) {
        cartRepo.removeItem(userId, productId);
        log.info("Item removed userId={} productId={}", userId, productId);
        return getCart(userId);
    }

    // ── Clear cart ────────────────────────────────────────────────────

    /**
     * Deletes the entire cart for the user.
     *
     * <p>Called explicitly via {@code DELETE /cart} and internally after a
     * successful checkout.
     *
     * @param userId the authenticated user
     */
    public void clearCart(UUID userId) {
        cartRepo.clearCart(userId);
        log.info("Cart cleared userId={}", userId);
    }

    // ── Checkout ──────────────────────────────────────────────────────

    /**
     * Validates the cart and submits an order to order-service.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load cart — throw {@link CartEmptyException} if empty.</li>
     *   <li>Snapshot current prices via ProductClient for each item.</li>
     *   <li>Check availability via InventoryClient for each item — collect all
     *       unavailable items and throw {@link ProductUnavailableException} if any.</li>
     *   <li>Build {@link CreateOrderRequest} with snapshotted unit prices.</li>
     *   <li>Call OrderClient — on success, clear the cart and return the order.</li>
     * </ol>
     *
     * <p>The cart is only cleared after a confirmed successful order creation.
     * If any step fails (including the OrderClient fallback), the cart is preserved
     * so the user can retry without re-adding items.
     *
     * @param userId         the authenticated user
     * @param request        contains the shipping address ID
     * @param idempotencyKey forwarded to order-service to deduplicate retries
     * @return the created order response from order-service
     * @throws CartEmptyException          if the cart has no items
     * @throws ResourceNotFoundException   if a product's price cannot be fetched
     * @throws ProductUnavailableException if one or more items are out of stock
     * @throws com.ecomm.cart.exception.ConflictException if order-service is unavailable
     */
    public OrderResponse checkout(UUID userId, CheckoutRequest request, String idempotencyKey) {
        // ── Step 1: load cart ──────────────────────────────────────────
        Map<Object, Object> raw = cartRepo.findAllItems(userId);
        if (raw.isEmpty()) {
            throw new CartEmptyException();
        }

        // ── Step 2: snapshot prices ────────────────────────────────────
        List<OrderItemRequest> orderItems   = new ArrayList<>();
        List<UUID>             unavailable  = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            UUID productId = UUID.fromString((String) entry.getKey());
            int  qty       = parseQty((String) entry.getValue(), productId);

            // Price snapshot — required for order creation
            ProductResponse product = productClient.getProduct(productId);
            if (product == null) {
                log.warn("Checkout blocked: cannot fetch price for productId={}", productId);
                throw new ResourceNotFoundException(
                        "Product " + productId + " is currently unavailable — please retry");
            }

            // ── Step 3: availability check ─────────────────────────────
            AvailabilityResponse availability =
                    inventoryClient.checkAvailability(productId, qty);

            if (!availability.isAvailable()) {
                unavailable.add(productId);
                log.warn("Checkout: productId={} qty={} is unavailable", productId, qty);
                continue; // collect all unavailable items before throwing
            }

            orderItems.add(OrderItemRequest.builder()
                    .productId(productId)
                    .qty(qty)
                    .unitPrice(product.getPrice())
                    .build());
        }

        // ── Fail if any items are unavailable ──────────────────────────
        if (!unavailable.isEmpty()) {
            throw new ProductUnavailableException(unavailable);
        }

        // ── Step 4: build and submit the order ─────────────────────────
        CreateOrderRequest createOrderRequest = CreateOrderRequest.builder()
                .userId(userId)
                .items(orderItems)
                .shippingAddressId(request.getShippingAddressId())
                .build();

        log.info("Submitting order for userId={} items={} idempotencyKey={}",
                userId, orderItems.size(), idempotencyKey);

        // OrderClientFallback throws ConflictException if order-service is unavailable
        OrderResponse orderResponse = orderClient.createOrder(idempotencyKey, createOrderRequest);

        // ── Step 5: clear cart on success ──────────────────────────────
        clearCart(userId);
        log.info("Checkout successful userId={} orderId={}", userId, orderResponse.getId());

        return orderResponse;
    }

    // ── Private helpers ───────────────────────────────────────────────

    /**
     * Builds an enriched {@link CartItemResponse} for one cart entry.
     *
     * <p>If the product-service call returns null (circuit open), a placeholder
     * response is built so {@code GET /cart} stays functional in degraded mode.
     */
    private CartItemResponse enrichItem(UUID productId, int qty) {
        ProductResponse product = productClient.getProduct(productId);

        if (product == null) {
            // Degraded mode: return item with placeholder data
            log.warn("Product enrichment unavailable for productId={} — using placeholder", productId);
            return CartItemResponse.builder()
                    .productId(productId)
                    .name("(product temporarily unavailable)")
                    .price(BigDecimal.ZERO)
                    .qty(qty)
                    .subtotal(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal price    = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));

        return CartItemResponse.builder()
                .productId(productId)
                .name(product.getName())
                .price(price)
                .qty(qty)
                .subtotal(subtotal)
                .build();
    }

    /**
     * Safely parses a quantity string stored in Redis.
     * Returns 1 on parse failure to keep the item visible rather than silently dropping it.
     */
    private int parseQty(String value, UUID productId) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            log.warn("Corrupt qty '{}' in Redis for productId={} — defaulting to 1", value, productId);
            return 1;
        }
    }
}
