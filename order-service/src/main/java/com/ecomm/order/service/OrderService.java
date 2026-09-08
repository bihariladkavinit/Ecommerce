package com.ecomm.order.service;

import com.ecomm.order.client.UserClient;
import com.ecomm.order.dto.client.AddressResponse;
import com.ecomm.order.dto.request.CreateOrderRequest;
import com.ecomm.order.dto.request.OrderItemRequest;
import com.ecomm.order.dto.response.OrderResponse;
import com.ecomm.order.dto.response.OrderStatusResponse;
import com.ecomm.order.entity.*;
import com.ecomm.order.exception.ConflictException;
import com.ecomm.order.exception.OrderNotCancellableException;
import com.ecomm.order.exception.ResourceNotFoundException;
import com.ecomm.order.mapper.OrderMapper;
import com.ecomm.order.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * REST-facing order service — handles creation, reads, and cancellation.
 *
 * <h3>Idempotency (POST /orders)</h3>
 * <ol>
 *   <li>Compute SHA-256 of the serialised request body.</li>
 *   <li>Look up the {@code Idempotency-Key} in {@code idempotency_keys}.</li>
 *   <li>If found and hash matches → return cached {@code response_body}.</li>
 *   <li>If found but hash differs → 409 Conflict.</li>
 *   <li>If not found → create order, write outbox, save idempotency row.</li>
 * </ol>
 *
 * <h3>Address resolution</h3>
 * The shipping address is resolved via {@link UserClient} (Feign, with CB
 * fallback). If user-service is unavailable the order is still created with
 * a minimal address placeholder — a user-service blip should not block orders.
 *
 * <h3>Saga kick-off</h3>
 * After persisting the order the service writes an
 * {@code inventory.reserve.command} outbox event in the same transaction.
 * The {@link OutboxRelayService} picks it up within 500 ms and publishes
 * it to Kafka, starting the saga.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository             orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final IdempotencyKeyRepository    idempotencyKeyRepository;
    private final OutboxEventRepository       outboxEventRepository;
    private final UserClient                  userClient;
    private final OrderMapper                 orderMapper;
    private final ObjectMapper                objectMapper;
    private final SagaOrchestrator            sagaOrchestrator;

    // ── Utility: current user ─────────────────────────────────────────

    /**
     * Extracts the authenticated user's UUID from Spring Security context.
     * {@link com.ecomm.order.config.GatewayAuthFilter} sets this as the principal.
     */
    public UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (principal instanceof UUID uuid) return uuid;
        throw new IllegalStateException("Principal is not a UUID: " + principal);
    }

    // ── Create ────────────────────────────────────────────────────────

    /**
     * Creates an order with idempotency, address resolution, and saga kick-off.
     * All DB writes happen in a single transaction.
     *
     * @param req            validated order request from Cart Service
     * @param idempotencyKey the {@code Idempotency-Key} header value
     * @return the created (or cached) order response
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req, String idempotencyKey) {

        // ── Step 1: idempotency check ──────────────────────────────────
        String requestHash = hash(req);
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findById(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyKey idem = existing.get();
            if (!idem.getRequestHash().equals(requestHash)) {
                throw new ConflictException(
                        "Idempotency key '" + idempotencyKey + "' was already used with a different request body");
            }
            // Same key + same body → return cached response
            log.info("Idempotent repeat for key={} — returning cached response", idempotencyKey);
            try {
                return objectMapper.readValue(idem.getResponseBody(), OrderResponse.class);
            } catch (JsonProcessingException ex) {
                log.error("Failed to deserialise cached response for key={}", idempotencyKey, ex);
                throw new ConflictException("Idempotency key exists but cached response is corrupt");
            }
        }

        // ── Step 2: resolve shipping address ──────────────────────────
        String shippingAddressJson = resolveAddress(req.getUserId(), req.getShippingAddressId());

        // ── Step 3: build order entity ────────────────────────────────
        BigDecimal totalAmount = req.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(req.getUserId())
                .status(OrderStatus.PENDING)
                .sagaState(SagaState.CREATED)
                .totalAmount(totalAmount)
                .shippingAddress(shippingAddressJson)
                .idempotencyKey(idempotencyKey)
                .compensationsPending(0)
                .build();

        // ── Step 4: build order items ──────────────────────────────────
        List<OrderItem> items = req.getItems().stream()
                .map(i -> OrderItem.builder()
                        .order(order)
                        .productId(i.getProductId())
                        .productName(i.getProductName() != null ? i.getProductName() : "Product " + i.getProductId())
                        .qty(i.getQty())
                        .unitPrice(i.getUnitPrice())
                        .build())
                .toList();
        order.getItems().addAll(items);

        // ── Step 5: persist order (cascades items) ─────────────────────
        Order saved = orderRepository.save(order);
        log.info("Order created id={} userId={} totalAmount={}", saved.getId(), saved.getUserId(), saved.getTotalAmount());

        // ── Step 6: write initial status history ───────────────────────
        historyRepository.save(OrderStatusHistory.builder()
                .orderId(saved.getId())
                .status(OrderStatus.PENDING.name())
                .build());

        // ── Step 7: write inventory.reserve.command to outbox ──────────
        writeReserveCommand(saved);

        // ── Step 8: build response and cache it ────────────────────────
        OrderResponse response = orderMapper.toResponse(saved);
        String responseJson = serialise(response);

        idempotencyKeyRepository.save(IdempotencyKey.builder()
                .key(idempotencyKey)
                .requestHash(requestHash)
                .responseBody(responseJson)
                .build());

        return response;
    }

    // ── Read ──────────────────────────────────────────────────────────

    /**
     * Returns a single order, enforcing ownership (own orders only, or ROLE_ADMIN).
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, UUID userId) {
        Order order = findOrderForUser(orderId, userId);
        return orderMapper.toResponse(order);
    }

    /**
     * Returns a paginated list of the authenticated user's orders, newest first.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(orderMapper::toResponse);
    }

    /**
     * Returns the lightweight status+sagaState view for polling.
     */
    @Transactional(readOnly = true)
    public OrderStatusResponse getOrderStatus(UUID orderId, UUID userId) {
        Order order = findOrderForUser(orderId, userId);
        return orderMapper.toStatusResponse(order);
    }

    // ── Cancel ────────────────────────────────────────────────────────

    /**
     * Cancels an order if it is in a cancellable saga state.
     *
     * <ul>
     *   <li>{@code CREATED} → cancel immediately (no compensation needed)</li>
     *   <li>{@code INVENTORY_RESERVED} → trigger compensation:
     *       publish {@code inventory.release.command}; order transitions to
     *       CANCELLED once the release reply is received</li>
     *   <li>Any state past {@code INVENTORY_RESERVED} (payment charged) → 409</li>
     * </ul>
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId) {
        Order order = findOrderForUser(orderId, userId);

        if (order.isTerminal()) {
            throw new OrderNotCancellableException(
                    "Order " + orderId + " is already in a terminal state: " + order.getSagaState());
        }

        if (!order.isCancellableByUser()) {
            throw new OrderNotCancellableException(
                    "Order " + orderId + " cannot be cancelled after payment has been charged " +
                    "(current state: " + order.getSagaState() + ")");
        }

        if (order.getSagaState() == SagaState.CREATED) {
            // No saga steps have been dispatched yet — cancel directly
            sagaOrchestrator.finalizeCancellation(orderId, "USER_CANCELLED");
        } else {
            // INVENTORY_RESERVED — release the stock reservation first
            sagaOrchestrator.startCompensationForUserCancel(orderId);
        }

        // Re-fetch to return the updated state
        Order updated = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return orderMapper.toResponse(updated);
    }

    // ── Private helpers ───────────────────────────────────────────────

    /**
     * Finds an order, verifying it belongs to the given user (or the caller
     * has ROLE_ADMIN).
     */
    private Order findOrderForUser(UUID orderId, UUID userId) {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        }

        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order " + orderId + " not found for user " + userId));
    }

    /**
     * Resolves the shipping address from user-service.
     * On fallback (empty list) or address not found, returns a minimal JSONB placeholder.
     */
    private String resolveAddress(UUID userId, UUID addressId) {
        try {
            List<AddressResponse> addresses = userClient.getAddresses(userId);
            Optional<AddressResponse> found = addresses.stream()
                    .filter(a -> addressId.equals(a.getId()))
                    .findFirst();

            if (found.isPresent()) {
                return objectMapper.writeValueAsString(found.get());
            }

            log.warn("Address {} not found for user {} — storing placeholder", addressId, userId);
        } catch (Exception ex) {
            log.warn("Failed to resolve address from user-service: {}", ex.getMessage());
        }

        // Fallback: store minimal placeholder so saga shipment step has something to use
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "addressId", addressId.toString(),
                    "resolved",  false
            ));
        } catch (JsonProcessingException ex) {
            return "{\"resolved\":false}";
        }
    }

    /**
     * Writes the initial {@code inventory.reserve.command} outbox event.
     * The payload contains the orderId and all items (productId + qty).
     */
    private void writeReserveCommand(Order order) {
        try {
            List<Map<String, Object>> itemPayloads = order.getItems().stream()
                    .map(i -> Map.<String, Object>of(
                            "productId", i.getProductId().toString(),
                            "qty",       i.getQty()))
                    .toList();

            String payload = objectMapper.writeValueAsString(Map.of(
                    "orderId", order.getId().toString(),
                    "items",   itemPayloads
            ));

            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType("Order")
                    .aggregateId(order.getId())
                    .eventType("inventory.reserve.command")
                    .payload(payload)
                    .published(false)
                    .build());

            log.debug("Wrote inventory.reserve.command to outbox for orderId={}", order.getId());

        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise inventory.reserve.command payload for orderId={}",
                    order.getId(), ex);
            throw new RuntimeException("Failed to write reserve command to outbox", ex);
        }
    }

    /** SHA-256 of the serialised request — used as the idempotency request hash. */
    private String hash(Object obj) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(obj);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            log.warn("Could not compute request hash — using empty string", ex);
            return "";
        }
    }

    /** Serialises an object to JSON string; throws RuntimeException on failure. */
    private String serialise(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialise response", ex);
        }
    }
}
