package com.ecomm.order.service;

import com.ecomm.order.entity.Order;
import com.ecomm.order.entity.SagaState;
import com.ecomm.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled reaper that detects orders stuck in non-terminal saga states
 * past their SLA thresholds and triggers the appropriate compensation path.
 *
 * <p>A saga can get stuck when a downstream service (inventory, payment, shipment)
 * never sends a reply — e.g. it crashed or the Kafka message was lost. Without
 * this reaper those orders would stay in PENDING forever with reserved stock and
 * potentially an uncaptured payment.
 *
 * <h3>Timeout thresholds (configurable in application.yml)</h3>
 * <ul>
 *   <li>{@code saga.timeout.inventory-seconds} (default 30 s) — applied to orders
 *       stuck in {@code CREATED} or {@code INVENTORY_RESERVED}</li>
 *   <li>{@code saga.timeout.payment-seconds} (default 60 s) — applied to orders
 *       stuck in {@code PAYMENT_COMPLETED}</li>
 *   <li>{@code saga.timeout.shipment-seconds} (default 120 s) — applied to orders
 *       stuck in {@code INVENTORY_CONFIRMED}</li>
 * </ul>
 *
 * <h3>Compensation mapping</h3>
 * <pre>
 *   CREATED              (> inventory-timeout) → onInventoryReservationFailed (cancel directly)
 *   INVENTORY_RESERVED   (> inventory-timeout) → compensatePaymentFailed (release stock)
 *   PAYMENT_COMPLETED    (> payment-timeout)   → compensateInventoryConfirmFailed (refund + release)
 *   INVENTORY_CONFIRMED  (> shipment-timeout)  → compensateShipmentFailed (refund + release)
 * </pre>
 *
 * <p>The reaper runs every 30 s with {@code fixedDelay} — not {@code fixedRate} —
 * so a slow scan cycle never causes overlapping executions.
 *
 * <p>Each compensation call is {@code @Transactional} on {@link SagaOrchestrator},
 * so a reaper crash mid-iteration leaves each processed order in a consistent state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutReaper {

    private final OrderRepository  orderRepository;
    private final SagaOrchestrator sagaOrchestrator;

    @Value("${saga.timeout.inventory-seconds:30}")
    private long inventoryTimeoutSeconds;

    @Value("${saga.timeout.payment-seconds:60}")
    private long paymentTimeoutSeconds;

    @Value("${saga.timeout.shipment-seconds:120}")
    private long shipmentTimeoutSeconds;

    /**
     * Runs every 30 seconds. Scans for stuck orders and triggers compensation.
     */
    @Scheduled(fixedDelay = 30_000)
    public void reap() {
        OffsetDateTime now = OffsetDateTime.now();

        reapCreated(now);
        reapInventoryReserved(now);
        reapPaymentCompleted(now);
        reapInventoryConfirmed(now);
    }

    // ── Per-state reapers ─────────────────────────────────────────────

    /**
     * Orders stuck in CREATED — the inventory reserve command was published but
     * no reply ever arrived. Cancel directly with no compensation.
     */
    private void reapCreated(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusSeconds(inventoryTimeoutSeconds);
        List<Order> stuck = orderRepository.findStuckOrders(SagaState.CREATED, cutoff);

        if (!stuck.isEmpty()) {
            log.warn("SagaTimeoutReaper: {} order(s) stuck in CREATED past {}s timeout",
                    stuck.size(), inventoryTimeoutSeconds);
        }

        for (Order order : stuck) {
            log.warn("Timeout: orderId={} stuck in CREATED since {} — cancelling directly",
                    order.getId(), order.getCreatedAt());
            try {
                sagaOrchestrator.onInventoryReservationFailed(order.getId());
            } catch (Exception ex) {
                log.error("Reaper failed to cancel orderId={}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Orders stuck in INVENTORY_RESERVED — payment charge command was published
     * but no reply arrived. Compensate by releasing the inventory reservation.
     */
    private void reapInventoryReserved(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusSeconds(inventoryTimeoutSeconds);
        List<Order> stuck = orderRepository.findStuckOrders(SagaState.INVENTORY_RESERVED, cutoff);

        if (!stuck.isEmpty()) {
            log.warn("SagaTimeoutReaper: {} order(s) stuck in INVENTORY_RESERVED past {}s timeout",
                    stuck.size(), inventoryTimeoutSeconds);
        }

        for (Order order : stuck) {
            log.warn("Timeout: orderId={} stuck in INVENTORY_RESERVED since {} — releasing stock",
                    order.getId(), order.getCreatedAt());
            try {
                sagaOrchestrator.compensatePaymentFailed(order.getId());
            } catch (Exception ex) {
                log.error("Reaper failed to compensate orderId={}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Orders stuck in PAYMENT_COMPLETED — inventory confirm command was published
     * but no reply arrived. Compensate by refunding and releasing.
     */
    private void reapPaymentCompleted(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusSeconds(paymentTimeoutSeconds);
        List<Order> stuck = orderRepository.findStuckOrders(SagaState.PAYMENT_COMPLETED, cutoff);

        if (!stuck.isEmpty()) {
            log.warn("SagaTimeoutReaper: {} order(s) stuck in PAYMENT_COMPLETED past {}s timeout",
                    stuck.size(), paymentTimeoutSeconds);
        }

        for (Order order : stuck) {
            log.warn("Timeout: orderId={} stuck in PAYMENT_COMPLETED since {} — refunding + releasing",
                    order.getId(), order.getCreatedAt());
            try {
                sagaOrchestrator.compensateInventoryConfirmFailed(order.getId());
            } catch (Exception ex) {
                log.error("Reaper failed to compensate orderId={}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Orders stuck in INVENTORY_CONFIRMED — shipment create command was published
     * but no reply arrived. Compensate by refunding and releasing.
     */
    private void reapInventoryConfirmed(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusSeconds(shipmentTimeoutSeconds);
        List<Order> stuck = orderRepository.findStuckOrders(SagaState.INVENTORY_CONFIRMED, cutoff);

        if (!stuck.isEmpty()) {
            log.warn("SagaTimeoutReaper: {} order(s) stuck in INVENTORY_CONFIRMED past {}s timeout",
                    stuck.size(), shipmentTimeoutSeconds);
        }

        for (Order order : stuck) {
            log.warn("Timeout: orderId={} stuck in INVENTORY_CONFIRMED since {} — refunding + releasing",
                    order.getId(), order.getCreatedAt());
            try {
                sagaOrchestrator.compensateShipmentFailed(order.getId());
            } catch (Exception ex) {
                log.error("Reaper failed to compensate orderId={}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
    }
}
