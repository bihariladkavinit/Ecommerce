package com.ecomm.inventory.kafka;

import com.ecomm.inventory.entity.ProcessedEvent;
import com.ecomm.inventory.kafka.dto.KafkaEnvelope;
import com.ecomm.inventory.repository.ProcessedEventRepository;
import com.ecomm.inventory.service.SagaInventoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kafka consumer for Order saga inventory commands.
 *
 * <p>Listens on three topics:
 * <ul>
 *   <li>{@code inventory.reserve.command} — reserve stock for an order</li>
 *   <li>{@code inventory.confirm.command} — confirm (deduct) reserved stock</li>
 *   <li>{@code inventory.release.command} — release (return) reserved stock</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * Before processing, each handler checks {@code processed_event(event_id)}. If
 * the record exists the message is a duplicate (at-least-once delivery) and is
 * acknowledged without reprocessing. The {@link ProcessedEvent} insert and the
 * business state change happen in the same {@code @Transactional} method on
 * {@link SagaInventoryService}, so the idempotency record is only persisted if
 * the business transaction commits.
 *
 * <h3>Manual acknowledgement</h3>
 * {@code ack-mode: RECORD} + manual {@link Acknowledgment} means the consumer
 * offset is only committed after the DB transaction has committed. This prevents
 * message loss on service restart.
 *
 * <h3>Error handling</h3>
 * Unhandled exceptions propagate to Spring Kafka's default error handler, which
 * retries with backoff and eventually sends to a dead-letter topic if configured.
 * Deserialization errors are caught here and the malformed message is skipped
 * (acknowledged) to avoid poison-pill consumer stalls.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryCommandConsumer {

    private final SagaInventoryService     sagaInventoryService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper             objectMapper;

    // ─────────────────────────────────────────────────────────────────
    // inventory.reserve.command
    // ─────────────────────────────────────────────────────────────────

    /**
     * Handles {@code inventory.reserve.command}.
     *
     * <p>Expected payload:
     * <pre>
     * { "orderId": "uuid", "items": [ { "productId": "uuid", "qty": 2 } ] }
     * </pre>
     */
    @KafkaListener(
            topics = "inventory.reserve.command",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onReserveCommand(@Payload String message, Acknowledgment ack) {
        log.debug("Received inventory.reserve.command: {}", message);

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload = envelope.getPayload();
            UUID     orderId = UUID.fromString(payload.get("orderId").asText());

            List<JsonNode> items = new ArrayList<>();
            payload.get("items").forEach(items::add);

            // Business logic + outbox write in SagaInventoryService (same @Transactional)
            sagaInventoryService.reserve(orderId, items);

            // Record idempotency key in same transaction
            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process inventory.reserve.command eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            // Re-throw so Spring Kafka error handler can retry / DLT
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // inventory.confirm.command
    // ─────────────────────────────────────────────────────────────────

    /**
     * Handles {@code inventory.confirm.command}.
     *
     * <p>Expected payload:
     * <pre>
     * { "orderId": "uuid" }
     * </pre>
     */
    @KafkaListener(
            topics = "inventory.confirm.command",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onConfirmCommand(@Payload String message, Acknowledgment ack) {
        log.debug("Received inventory.confirm.command: {}", message);

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            UUID orderId = UUID.fromString(envelope.getPayload().get("orderId").asText());

            sagaInventoryService.confirm(orderId);

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process inventory.confirm.command eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // inventory.release.command
    // ─────────────────────────────────────────────────────────────────

    /**
     * Handles {@code inventory.release.command}.
     *
     * <p>Expected payload:
     * <pre>
     * { "orderId": "uuid", "reason": "PAYMENT_FAILED" }
     * </pre>
     */
    @KafkaListener(
            topics = "inventory.release.command",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onReleaseCommand(@Payload String message, Acknowledgment ack) {
        log.debug("Received inventory.release.command: {}", message);

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            UUID   orderId = UUID.fromString(envelope.getPayload().get("orderId").asText());
            String reason  = envelope.getPayload().has("reason")
                    ? envelope.getPayload().get("reason").asText()
                    : "UNSPECIFIED";

            log.info("Processing release command orderId={} reason={}", orderId, reason);
            sagaInventoryService.release(orderId);

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process inventory.release.command eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Parses the raw JSON string into a {@link KafkaEnvelope}.
     * If parsing fails the message is acknowledged (to avoid consumer stall)
     * and {@code null} is returned so the caller can exit immediately.
     */
    private KafkaEnvelope parseEnvelope(String message, Acknowledgment ack) {
        try {
            return objectMapper.readValue(message, KafkaEnvelope.class);
        } catch (Exception ex) {
            log.error("Malformed Kafka message — skipping: {}", ex.getMessage());
            ack.acknowledge(); // poison-pill guard
            return null;
        }
    }

    /**
     * Returns {@code true} (and acknowledges) if this {@code eventId} has
     * already been processed. Caller should return immediately when {@code true}.
     */
    private boolean isDuplicate(UUID eventId, Acknowledgment ack) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event detected, skipping eventId={}", eventId);
            ack.acknowledge();
            return true;
        }
        return false;
    }

    /** Persists the idempotency record — must be called within an active transaction. */
    private void recordProcessed(UUID eventId) {
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .build());
    }
}
