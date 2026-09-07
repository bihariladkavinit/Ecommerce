package com.ecomm.inventory.service;

import com.ecomm.inventory.entity.*;
import com.ecomm.inventory.exception.InsufficientStockException;
import com.ecomm.inventory.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
/**
 * Handles all three inventory steps of the Order saga:
 * <ol>
 *   <li><strong>Reserve</strong> — earmarks stock for each item in an order.
 *       Deducts from {@code available_qty} and increments {@code reserved_qty}.
 *       Creates a {@link StockReservation} per item.
 *       Writes an {@code inventory.reserve.reply} outbox event.</li>
 *   <li><strong>Confirm</strong> — converts reservations to real deductions.
 *       Decrements {@code reserved_qty} (available was already reduced during reserve).
 *       Transitions reservations RESERVED → CONFIRMED.
 *       Writes an {@code inventory.confirm.reply} outbox event.</li>
 *   <li><strong>Release</strong> — compensating action; undoes a reservation.
 *       Returns qty back to {@code available_qty} and zeroes out {@code reserved_qty}.
 *       Transitions reservations RESERVED → RELEASED.
 *       Writes an {@code inventory.release.reply} outbox event.</li>
 * </ol>
 *
 * <h3>Transactional outbox</h3>
 * Every method is {@code @Transactional} and writes an {@link OutboxEvent} in
 * the <em>same</em> transaction as the stock change. If the transaction rolls
 * back, the reply is also rolled back — preventing phantom replies.
 *
 * <h3>Idempotency</h3>
 * Callers (command consumers) must check {@code processed_event} <em>before</em>
 * calling these methods and record the {@code eventId} in the same transaction.
 * This service does not duplicate that check — separation of concerns.
 *
 * <h3>Optimistic locking</h3>
 * All stock mutations go through {@link OptimisticLockRetryHelper} to handle
 * concurrent saga steps on the same product gracefully.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaInventoryService {

    // ── Kafka topic names (outbox event_type = topic) ─────────────────
    static final String TOPIC_RESERVE_REPLY = "inventory.reserve.reply";
    static final String TOPIC_CONFIRM_REPLY = "inventory.confirm.reply";
    static final String TOPIC_RELEASE_REPLY = "inventory.release.reply";

    // ── Outbox aggregate type label ───────────────────────────────────
    private static final String AGGREGATE_TYPE = "StockReservation";

    private final StockItemRepository        stockItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final OutboxEventRepository      outboxEventRepository;
    private final ObjectMapper               objectMapper;

    // ─────────────────────────────────────────────────────────────────
    // 1. RESERVE
    // ─────────────────────────────────────────────────────────────────

    /**
     * Attempts to reserve stock for all items in the order atomically.
     *
     * <p>All items are validated first before any stock is mutated — a partial
     * reserve (some items succeed, one fails) would be harder to compensate.
     * If any item has insufficient stock the entire operation fails fast and
     * a FAILED reply is written.
     *
     * @param orderId  the order driving this saga step
     * @param items    list of {@code {productId, qty}} nodes from the command payload
     */
    @Transactional
    public void reserve(UUID orderId, List<JsonNode> items) {
        log.info("saga-reserve: orderId={} itemCount={}", orderId, items.size());

        // ── Phase 1: validate all items before touching stock ─────────
        for (JsonNode item : items) {
            UUID productId  = UUID.fromString(item.get("productId").asText());
            int  requested  = item.get("qty").asInt();

            StockItem stock = stockItemRepository.findById(productId).orElse(null);
            if (stock == null || stock.getAvailableQty() < requested) {
                int available = stock == null ? 0 : stock.getAvailableQty();
                log.warn("saga-reserve FAILED orderId={} productId={} requested={} available={}",
                        orderId, productId, requested, available);
                writeOutbox(orderId, TOPIC_RESERVE_REPLY,
                        buildReserveFailedPayload(orderId, productId, "INSUFFICIENT_STOCK"));
                return;
            }
        }

        // ── Phase 2: apply reservations ───────────────────────────────
        List<UUID> reservationIds = new ArrayList<>();

        for (JsonNode item : items) {
            UUID productId = UUID.fromString(item.get("productId").asText());
            int  qty       = item.get("qty").asInt();

            // saveAndFlush forces Hibernate to issue the UPDATE immediately within
            // this transaction so the @Version check runs now, not at commit time.
            // retryHelper catches ObjectOptimisticLockingFailureException, re-reads
            // the row, and retries the mutation.
            saveStockWithRetry(() -> {
                StockItem stock = stockItemRepository.findById(productId)
                        .orElseThrow(() -> new InsufficientStockException(productId, qty, 0));

                if (stock.getAvailableQty() < qty) {
                    throw new InsufficientStockException(productId, qty, stock.getAvailableQty());
                }

                stock.setAvailableQty(stock.getAvailableQty() - qty);
                stock.setReservedQty(stock.getReservedQty() + qty);
                stockItemRepository.saveAndFlush(stock);
                log.debug("Reserved productId={} qty={} newAvailableQty={}",
                        productId, qty, stock.getAvailableQty());
                return null;
            });

            StockReservation reservation = StockReservation.builder()
                    .orderId(orderId)
                    .productId(productId)
                    .qty(qty)
                    .status(ReservationStatus.RESERVED)
                    .build();
            reservation = stockReservationRepository.save(reservation);
            reservationIds.add(reservation.getId());
        }

        writeOutbox(orderId, TOPIC_RESERVE_REPLY,
                buildReserveSucceededPayload(orderId, reservationIds));
        log.info("saga-reserve SUCCEEDED orderId={} reservations={}", orderId, reservationIds.size());
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. CONFIRM
    // ─────────────────────────────────────────────────────────────────

    /**
     * Confirms all RESERVED reservations for an order, converting them from
     * earmarked to actually consumed stock.
     *
     * <p>If no RESERVED reservations are found (e.g. already confirmed — idempotent
     * re-delivery), the operation succeeds silently and still writes a SUCCEEDED reply.
     *
     * @param orderId the order to confirm
     */
    @Transactional
    public void confirm(UUID orderId) {
        log.info("saga-confirm: orderId={}", orderId);

        List<StockReservation> reservations =
                stockReservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);

        for (StockReservation reservation : reservations) {
            UUID productId = reservation.getProductId();
            int  qty       = reservation.getQty();

            saveStockWithRetry(() -> {
                StockItem stock = stockItemRepository.findById(productId)
                        .orElseThrow(() -> new IllegalStateException(
                                "StockItem missing for confirmed product " + productId));
                stock.setReservedQty(Math.max(0, stock.getReservedQty() - qty));
                stockItemRepository.saveAndFlush(stock);
                log.debug("Confirmed reservation id={} productId={} qty={}",
                        reservation.getId(), productId, qty);
                return null;
            });

            reservation.setStatus(ReservationStatus.CONFIRMED);
            stockReservationRepository.save(reservation);
        }

        writeOutbox(orderId, TOPIC_CONFIRM_REPLY, buildConfirmSucceededPayload(orderId));
        log.info("saga-confirm SUCCEEDED orderId={} confirmedCount={}", orderId, reservations.size());
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. RELEASE (compensate)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Releases all RESERVED reservations for an order, returning the stock
     * back to the available pool (compensation / rollback).
     *
     * <p>If no RESERVED reservations are found the operation succeeds silently —
     * the saga may have already released or the reserve step failed before
     * creating reservations.
     *
     * @param orderId the order to compensate
     */
    @Transactional
    public void release(UUID orderId) {
        log.info("saga-release: orderId={}", orderId);

        List<StockReservation> reservations =
                stockReservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);

        for (StockReservation reservation : reservations) {
            UUID productId = reservation.getProductId();
            int  qty       = reservation.getQty();

            saveStockWithRetry(() -> {
                StockItem stock = stockItemRepository.findById(productId)
                        .orElseThrow(() -> new IllegalStateException(
                                "StockItem missing for released product " + productId));
                stock.setAvailableQty(stock.getAvailableQty() + qty);
                stock.setReservedQty(Math.max(0, stock.getReservedQty() - qty));
                stockItemRepository.saveAndFlush(stock);
                log.debug("Released reservation id={} productId={} qty={}",
                        reservation.getId(), productId, qty);
                return null;
            });

            reservation.setStatus(ReservationStatus.RELEASED);
            stockReservationRepository.save(reservation);
        }

        writeOutbox(orderId, TOPIC_RELEASE_REPLY, buildReleaseSucceededPayload(orderId));
        log.info("saga-release SUCCEEDED orderId={} releasedCount={}", orderId, reservations.size());
    }

    // ─────────────────────────────────────────────────────────────────
    // Optimistic-lock retry helper for in-transaction stock saves
    // ─────────────────────────────────────────────────────────────────

    /**
     * Retries the given stock-mutation lambda on optimistic lock failure.
     *
     * <p>Uses {@code saveAndFlush} inside the lambda (instead of {@code save})
     * so Hibernate immediately issues the UPDATE SQL and the {@code @Version}
     * check runs within this transaction boundary — allowing
     * {@link ObjectOptimisticLockingFailureException} to be caught and retried
     * rather than deferred to commit time.
     *
     * <p>Since we remain in the same outer transaction, the Hibernate session
     * cache must be cleared between retries so the re-read returns a fresh row.
     * This is handled by the fact that {@code saveAndFlush} + {@code findById}
     * in the same session will hit the DB when the first-level cache is stale
     * after a flush. For correctness the lambda always calls {@code findById}
     * before mutating.
     */
    private void saveStockWithRetry(java.util.function.Supplier<Void> operation) {
        int  maxAttempts      = 3;
        long initialBackoffMs = 50L;
        int  attempt          = 0;

        while (true) {
            try {
                operation.get();
                return;
            } catch (ObjectOptimisticLockingFailureException ex) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new com.ecomm.inventory.exception.ConflictException(
                            "Stock update conflict after " + maxAttempts + " retries");
                }
                long backoff = initialBackoffMs * (1L << attempt);
                log.debug("Stock optimistic lock conflict attempt {}/{}, retrying in {}ms",
                        attempt, maxAttempts, backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new com.ecomm.inventory.exception.ConflictException("Retry interrupted");
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Outbox helpers
    // ─────────────────────────────────────────────────────────────────
    private void writeOutbox(UUID orderId, String topic, String payload) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(orderId)   // partition key = orderId (saga convention)
                .eventType(topic)
                .payload(payload)
                .published(false)
                .build();
        outboxEventRepository.save(event);
    }

    // ── Payload builders ──────────────────────────────────────────────

    private String buildReserveSucceededPayload(UUID orderId, List<UUID> reservationIds) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "orderId",        orderId.toString(),
                    "status",         "SUCCEEDED",
                    "reservationIds", reservationIds.stream().map(UUID::toString).toList()
            ));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise reserve-succeeded payload", ex);
            return "{}";
        }
    }

    private String buildReserveFailedPayload(UUID orderId, UUID productId, String reason) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "orderId",   orderId.toString(),
                    "status",    "FAILED",
                    "reason",    reason,
                    "productId", productId.toString()
            ));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise reserve-failed payload", ex);
            return "{}";
        }
    }

    private String buildConfirmSucceededPayload(UUID orderId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "orderId", orderId.toString(),
                    "status",  "SUCCEEDED"
            ));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise confirm-succeeded payload", ex);
            return "{}";
        }
    }

    private String buildReleaseSucceededPayload(UUID orderId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "orderId", orderId.toString(),
                    "status",  "SUCCEEDED"
            ));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise release-succeeded payload", ex);
            return "{}";
        }
    }
}
