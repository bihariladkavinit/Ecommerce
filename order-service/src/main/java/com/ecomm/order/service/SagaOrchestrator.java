package com.ecomm.order.service;

import com.ecomm.order.entity.*;
import com.ecomm.order.repository.OrderRepository;
import com.ecomm.order.repository.OrderStatusHistoryRepository;
import com.ecomm.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Saga state machine orchestrator for the order lifecycle.
 *
 * <p>Every public method corresponds to one saga step or compensation action.
 * All methods are {@code @Transactional} — the saga state update and the
 * outbox event write happen in the same DB transaction, ensuring they are
 * always consistent. If the transaction rolls back, neither the state change
 * nor the outbox event is persisted.
 *
 * <h3>Stale-state guard</h3>
 * Every method checks that the order's current {@link SagaState} matches the
 * expected precondition before applying the transition. If it doesn't (e.g.
 * a duplicate Kafka reply arrives after the state has already advanced), the
 * method is a no-op — this makes every transition idempotent at the saga level.
 *
 * <h3>Happy-path state machine</h3>
 * <pre>
 * CREATED → [inventory.reserve.command published by OrderService]
 *   onInventoryReserved()     → INVENTORY_RESERVED  + payment.charge.command
 *   onPaymentCompleted()      → PAYMENT_COMPLETED   + inventory.confirm.command
 *   onInventoryConfirmed()    → INVENTORY_CONFIRMED + shipment.create.command
 *   onShipmentCreated()       → SHIPMENT_CREATED    → CONFIRMED + order.confirmed
 * </pre>
 *
 * <h3>Compensation paths</h3>
 * <pre>
 * onInventoryReservationFailed()   → CANCELLED + order.cancelled
 * compensatePaymentFailed()        → COMPENSATING + inventory.release.command (1 pending)
 * compensateInventoryConfirmFailed()→ COMPENSATING + refund + release (2 pending)
 * compensateShipmentFailed()       → COMPENSATING + refund + release (2 pending)
 * startCompensationForUserCancel() → COMPENSATING + inventory.release.command (1 pending)
 * decrementAndFinalize()           → called by each compensation reply; CANCELLED when 0
 * finalizeCancellation()           → CANCELLED + order.cancelled
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    // ── Topic constants ───────────────────────────────────────────────
    static final String TOPIC_PAYMENT_CHARGE      = "payment.charge.command";
    static final String TOPIC_INVENTORY_CONFIRM   = "inventory.confirm.command";
    static final String TOPIC_SHIPMENT_CREATE     = "shipment.create.command";
    static final String TOPIC_INVENTORY_RELEASE   = "inventory.release.command";
    static final String TOPIC_PAYMENT_REFUND      = "payment.refund.command";
    static final String TOPIC_ORDER_CONFIRMED     = "order.confirmed";
    static final String TOPIC_ORDER_CANCELLED     = "order.cancelled";

    private static final String AGGREGATE_TYPE = "Order";

    private final OrderRepository             orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OutboxEventRepository       outboxEventRepository;
    private final ObjectMapper                objectMapper;

    // ─────────────────────────────────────────────────────────────────
    // HAPPY PATH
    // ─────────────────────────────────────────────────────────────────

    /**
     * Handles {@code inventory.reserve.reply SUCCEEDED}.
     * Advances: CREATED → INVENTORY_RESERVED, publishes payment.charge.command.
     */
    @Transactional
    public void onInventoryReserved(UUID orderId) {
        Order order = load(orderId);
        if (order == null) return;

        if (order.getSagaState() != SagaState.CREATED) {
            log.warn("onInventoryReserved: unexpected state {} for orderId={} — skipping",
                    order.getSagaState(), orderId);
            return;
        }

        order.setSagaState(SagaState.INVENTORY_RESERVED);
        orderRepository.save(order);

        String payload = toJson(Map.of(
                "orderId", orderId.toString(),
                "userId",  order.getUserId().toString(),
                "amount",  order.getTotalAmount().toPlainString()
        ));
        writeOutbox(orderId, TOPIC_PAYMENT_CHARGE, payload);

        log.info("saga: INVENTORY_RESERVED orderId={} → published payment.charge.command", orderId);
    }

    /**
     * Handles {@code payment.charge.reply SUCCEEDED}.
     * Advances: INVENTORY_RESERVED → PAYMENT_COMPLETED, publishes inventory.confirm.command.
     *
     * @param paymentId the paymentId from the reply — stored on the order for compensation
     */
    @Transactional
    public void onPaymentCompleted(UUID orderId, UUID paymentId) {
        Order order = load(orderId);
        if (order == null) return;

        if (order.getSagaState() != SagaState.INVENTORY_RESERVED) {
            log.warn("onPaymentCompleted: unexpected state {} for orderId={} — skipping",
                    order.getSagaState(), orderId);
            return;
        }

        order.setSagaState(SagaState.PAYMENT_COMPLETED);
        order.setPaymentId(paymentId);
        orderRepository.save(order);

        String payload = toJson(Map.of("orderId", orderId.toString()));
        writeOutbox(orderId, TOPIC_INVENTORY_CONFIRM, payload);

        log.info("saga: PAYMENT_COMPLETED orderId={} paymentId={} → published inventory.confirm.command",
                orderId, paymentId);
    }

    /**
     * Handles {@code inventory.confirm.reply SUCCEEDED}.
     * Advances: PAYMENT_COMPLETED → INVENTORY_CONFIRMED, publishes shipment.create.command.
     */
    @Transactional
    public void onInventoryConfirmed(UUID orderId) {
        Order order = load(orderId);
        if (order == null) return;

        if (order.getSagaState() != SagaState.PAYMENT_COMPLETED) {
            log.warn("onInventoryConfirmed: unexpected state {} for orderId={} — skipping",
                    order.getSagaState(), orderId);
            return;
        }

        order.setSagaState(SagaState.INVENTORY_CONFIRMED);
        orderRepository.save(order);

        // Deserialise the shipping address snapshot for the shipment command
        String shippingAddressJson = order.getShippingAddress() != null
                ? order.getShippingAddress() : "{}";

        String payload = toJson(Map.of(
                "orderId",         orderId.toString(),
                "userId",          order.getUserId().toString(),
                "shippingAddress", rawJson(shippingAddressJson)
        ));
        writeOutbox(orderId, TOPIC_SHIPMENT_CREATE, payload);

        log.info("saga: INVENTORY_CONFIRMED orderId={} → published shipment.create.command", orderId);
    }

    /**
     * Handles {@code shipment.create.reply SUCCEEDED}.
     * Advances: INVENTORY_CONFIRMED → SHIPMENT_CREATED → CONFIRMED, publishes order.confirmed.
     */
    @Transactional
    public void onShipmentCreated(UUID orderId) {
        Order order = load(orderId);
        if (order == null) return;

        if (order.getSagaState() != SagaState.INVENTORY_CONFIRMED) {
            log.warn("onShipmentCreated: unexpected state {} for orderId={} — skipping",
                    order.getSagaState(), orderId);
            return;
        }

        order.setSagaState(SagaState.CONFIRMED);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        historyRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .status(OrderStatus.CONFIRMED.name())
                .build());

        String payload = toJson(Map.of(
                "orderId",     orderId.toString(),
                "userId",      order.getUserId().toString(),
                "totalAmount", order.getTotalAmount().toPlainString()
        ));
        writeOutbox(orderId, TOPIC_ORDER_CONFIRMED, payload);

        log.info("saga: CONFIRMED orderId={} → published order.confirmed", orderId);
    }

    // ─────────────────────────────────────────────────────────────────
    // COMPENSATION PATHS
    // ─────────────────────────────────────────────────────────────────

    /**
     * Handles {@code inventory.reserve.reply FAILED} — no compensation needed.
     * Directly finalises cancellation.
     */
    @Transactional
    public void onInventoryReservationFailed(UUID orderId) {
        Order order = load(orderId);
        if (order == null) return;

        if (order.getSagaState() != SagaState.CREATED) {
            log.warn("onInventoryReservationFailed: unexpected state {} for orderId={} — skipping",
                    order.getSagaState(), orderId);
            return;
        }

        log.info("saga: inventory reservation FAILED for orderId={} → cancelling directly", orderId);
        finalizeCancellation(orderId, "INSUFFICIENT_STOCK");
    }

    /**
     * Handles {@code payment.charge.reply FAILED}.
     * Publishes {@code inventory.release.command} (1 compensation pending).
     */
    @Transactional
    public void compensatePaymentFailed(UUID orderId) {
        startCompensation(orderId, SagaState.INVENTORY_RESERVED, 1, "PAYMENT_FAILED");
    }

    /**
     * Handles {@code inventory.confirm.reply FAILED} (rare — DB error etc.).
     * Publishes both {@code payment.refund.command} and {@code inventory.release.command}
     * (2 compensations pending).
     */
    @Transactional
    public void compensateInventoryConfirmFailed(UUID orderId) {
        startCompensation(orderId, SagaState.PAYMENT_COMPLETED, 2, "INVENTORY_CONFIRM_FAILED");
    }

    /**
     * Handles {@code shipment.create.reply FAILED}.
     * Publishes both {@code payment.refund.command} and {@code inventory.release.command}
     * (2 compensations pending).
     */
    @Transactional
    public void compensateShipmentFailed(UUID orderId) {
        startCompensation(orderId, SagaState.INVENTORY_CONFIRMED, 2, "SHIPMENT_CREATION_FAILED");
    }

    /**
     * User-initiated cancel when saga is in INVENTORY_RESERVED state.
     * Publishes {@code inventory.release.command} (1 compensation pending).
     */
    @Transactional
    public void startCompensationForUserCancel(UUID orderId) {
        startCompensation(orderId, SagaState.INVENTORY_RESERVED, 1, "USER_CANCELLED");
    }

    /**
     * Core compensation starter — sets COMPENSATING, writes the appropriate
     * compensating commands to the outbox, and sets {@code compensationsPending}.
     *
     * @param orderId         the order to compensate
     * @param expectedState   the saga state we expect to see (stale-state guard)
     * @param compensations   1 = inventory only; 2 = inventory + payment refund
     * @param reason          human-readable reason stored in commands
     */
    @Transactional
    public void startCompensation(UUID orderId, SagaState expectedState,
                                  int compensations, String reason) {
        Order order = load(orderId);
        if (order == null) return;

        if (order.getSagaState() != expectedState) {
            log.warn("startCompensation: unexpected state {} (expected {}) for orderId={} — skipping",
                    order.getSagaState(), expectedState, orderId);
            return;
        }

        order.setSagaState(SagaState.COMPENSATING);
        order.setCompensationsPending(compensations);
        orderRepository.save(order);

        // Always release inventory
        String releasePayload = toJson(Map.of(
                "orderId", orderId.toString(),
                "reason",  reason
        ));
        writeOutbox(orderId, TOPIC_INVENTORY_RELEASE, releasePayload);

        // Refund payment if a charge was already made (2-compensation cases)
        if (compensations == 2 && order.getPaymentId() != null) {
            String refundPayload = toJson(Map.of(
                    "orderId",   orderId.toString(),
                    "paymentId", order.getPaymentId().toString(),
                    "amount",    order.getTotalAmount().toPlainString(),
                    "reason",    reason
            ));
            writeOutbox(orderId, TOPIC_PAYMENT_REFUND, refundPayload);
        }

        log.info("saga: COMPENSATING orderId={} reason={} compensations={}", orderId, reason, compensations);
    }

    /**
     * Called by each compensation reply consumer (inventory.release.reply,
     * payment.refund.reply). Decrements {@code compensationsPending} and
     * calls {@link #finalizeCancellation} when it reaches 0.
     *
     * @param orderId the order whose compensation reply just arrived
     * @param reason  forwarded to finalizeCancellation for the order.cancelled event
     */
    @Transactional
    public void decrementAndFinalize(UUID orderId, String reason) {
        Order order = load(orderId);
        if (order == null) return;

        if (order.getSagaState() != SagaState.COMPENSATING) {
            log.warn("decrementAndFinalize: order {} is not COMPENSATING (state={}) — skipping",
                    orderId, order.getSagaState());
            return;
        }

        int remaining = order.getCompensationsPending() - 1;
        order.setCompensationsPending(Math.max(0, remaining));
        orderRepository.save(order);

        log.debug("saga: compensation reply received orderId={} remaining={}", orderId, remaining);

        if (remaining <= 0) {
            finalizeCancellation(orderId, reason);
        }
    }

    /**
     * Terminal cancellation — sets CANCELLED status, writes history entry,
     * and publishes {@code order.cancelled} to the outbox.
     *
     * <p>Idempotent: if the order is already CANCELLED this is a no-op.
     */
    @Transactional
    public void finalizeCancellation(UUID orderId, String reason) {
        Order order = load(orderId);
        if (order == null) return;

        if (order.getSagaState() == SagaState.CANCELLED) {
            log.debug("finalizeCancellation: orderId={} already CANCELLED — skipping", orderId);
            return;
        }

        order.setSagaState(SagaState.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        historyRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .status(OrderStatus.CANCELLED.name())
                .build());

        String payload = toJson(Map.of(
                "orderId", orderId.toString(),
                "userId",  order.getUserId().toString(),
                "reason",  reason
        ));
        writeOutbox(orderId, TOPIC_ORDER_CANCELLED, payload);

        log.info("saga: CANCELLED orderId={} reason={}", orderId, reason);
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    private Order load(UUID orderId) {
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) {
            log.error("SagaOrchestrator: order {} not found — cannot advance saga", orderId);
            return null;
        }
        return opt.get();
    }

    private void writeOutbox(UUID orderId, String topic, String payload) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(orderId)
                .eventType(topic)
                .payload(payload)
                .published(false)
                .build());
    }

    /** Serialises a Map to JSON; throws RuntimeException on failure. */
    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialise outbox payload", ex);
        }
    }

    /**
     * Returns an opaque object that Jackson will embed as a nested JSON node
     * rather than a double-escaped string. Used for the shippingAddress field
     * which is already stored as a JSON string in the DB.
     */
    private Object rawJson(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, Object.class);
        } catch (JsonProcessingException ex) {
            return jsonString; // fallback: embed as string
        }
    }
}
