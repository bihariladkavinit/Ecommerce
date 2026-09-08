package com.ecomm.order.kafka;

import com.ecomm.order.entity.ProcessedEvent;
import com.ecomm.order.kafka.dto.KafkaEnvelope;
import com.ecomm.order.repository.ProcessedEventRepository;
import com.ecomm.order.service.SagaOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Kafka consumer for all Order saga reply topics.
 *
 * <p>Listens on six topics:
 * <ul>
 *   <li>{@code inventory.reserve.reply}  — SUCCEEDED → advance; FAILED → cancel directly</li>
 *   <li>{@code payment.charge.reply}     — SUCCEEDED → advance; FAILED → compensate (release)</li>
 *   <li>{@code inventory.confirm.reply}  — SUCCEEDED → advance; FAILED → compensate (refund+release)</li>
 *   <li>{@code shipment.create.reply}    — SUCCEEDED → confirm; FAILED → compensate (refund+release)</li>
 *   <li>{@code inventory.release.reply}  — SUCCEEDED → decrement compensation counter</li>
 *   <li>{@code payment.refund.reply}     — SUCCEEDED → decrement compensation counter</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * Before processing, each handler checks {@code processed_event(event_id)}.
 * If found the message is a duplicate (at-least-once delivery) — ack and skip.
 * The {@link ProcessedEvent} insert and the saga state change happen in the
 * same {@code @Transactional} method, so a rollback removes the record too,
 * preventing false deduplication on the next retry.
 *
 * <h3>Manual acknowledgement</h3>
 * Offsets are committed only after the DB transaction commits. A crash between
 * consume and commit will re-deliver the message, which the idempotency check
 * handles safely.
 *
 * <h3>Error handling</h3>
 * Malformed/unparseable messages are acknowledged immediately (poison-pill guard).
 * All other exceptions propagate to Spring Kafka's error handler for retry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyConsumer {

    private final SagaOrchestrator         sagaOrchestrator;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper             objectMapper;

    // ─────────────────────────────────────────────────────────────────
    // 1. inventory.reserve.reply
    // ─────────────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "inventory.reserve.reply",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onInventoryReserveReply(@Payload String message, Acknowledgment ack) {
        log.debug("Received inventory.reserve.reply");

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;
        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload = envelope.getPayload();
            UUID   orderId = extractOrderId(payload);
            String status  = payload.get("status").asText();

            if ("SUCCEEDED".equals(status)) {
                sagaOrchestrator.onInventoryReserved(orderId);
            } else {
                String reason = payload.has("reason") ? payload.get("reason").asText() : "RESERVE_FAILED";
                log.warn("inventory.reserve.reply FAILED orderId={} reason={}", orderId, reason);
                sagaOrchestrator.onInventoryReservationFailed(orderId);
            }

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process inventory.reserve.reply eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. payment.charge.reply
    // ─────────────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "payment.charge.reply",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onPaymentChargeReply(@Payload String message, Acknowledgment ack) {
        log.debug("Received payment.charge.reply");

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;
        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload  = envelope.getPayload();
            UUID   orderId   = extractOrderId(payload);
            String status    = payload.get("status").asText();

            if ("SUCCEEDED".equals(status)) {
                UUID paymentId = UUID.fromString(payload.get("paymentId").asText());
                sagaOrchestrator.onPaymentCompleted(orderId, paymentId);
            } else {
                String reason = payload.has("reason") ? payload.get("reason").asText() : "PAYMENT_FAILED";
                log.warn("payment.charge.reply FAILED orderId={} reason={}", orderId, reason);
                sagaOrchestrator.compensatePaymentFailed(orderId);
            }

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process payment.charge.reply eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. inventory.confirm.reply
    // ─────────────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "inventory.confirm.reply",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onInventoryConfirmReply(@Payload String message, Acknowledgment ack) {
        log.debug("Received inventory.confirm.reply");

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;
        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload = envelope.getPayload();
            UUID   orderId = extractOrderId(payload);
            String status  = payload.get("status").asText();

            if ("SUCCEEDED".equals(status)) {
                sagaOrchestrator.onInventoryConfirmed(orderId);
            } else {
                log.warn("inventory.confirm.reply FAILED orderId={}", orderId);
                sagaOrchestrator.compensateInventoryConfirmFailed(orderId);
            }

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process inventory.confirm.reply eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. shipment.create.reply
    // ─────────────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "shipment.create.reply",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onShipmentCreateReply(@Payload String message, Acknowledgment ack) {
        log.debug("Received shipment.create.reply");

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;
        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload = envelope.getPayload();
            UUID   orderId = extractOrderId(payload);
            String status  = payload.get("status").asText();

            if ("SUCCEEDED".equals(status)) {
                sagaOrchestrator.onShipmentCreated(orderId);
            } else {
                log.warn("shipment.create.reply FAILED orderId={}", orderId);
                sagaOrchestrator.compensateShipmentFailed(orderId);
            }

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process shipment.create.reply eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. inventory.release.reply
    // ─────────────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "inventory.release.reply",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onInventoryReleaseReply(@Payload String message, Acknowledgment ack) {
        log.debug("Received inventory.release.reply");

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;
        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload = envelope.getPayload();
            UUID   orderId = extractOrderId(payload);
            String status  = payload.get("status").asText();

            if ("SUCCEEDED".equals(status)) {
                // Decrement compensation counter; finalise if it reaches 0
                sagaOrchestrator.decrementAndFinalize(orderId, "COMPENSATION_COMPLETE");
            } else {
                // Release failure is unusual — log and still decrement so saga doesn't hang
                log.error("inventory.release.reply FAILED for orderId={} — forcing cancellation", orderId);
                sagaOrchestrator.decrementAndFinalize(orderId, "RELEASE_FAILED");
            }

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process inventory.release.reply eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 6. payment.refund.reply
    // ─────────────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "payment.refund.reply",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onPaymentRefundReply(@Payload String message, Acknowledgment ack) {
        log.debug("Received payment.refund.reply");

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;
        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload = envelope.getPayload();
            UUID   orderId = extractOrderId(payload);
            String status  = payload.get("status").asText();

            if ("SUCCEEDED".equals(status)) {
                sagaOrchestrator.decrementAndFinalize(orderId, "COMPENSATION_COMPLETE");
            } else {
                log.error("payment.refund.reply FAILED for orderId={} — forcing cancellation", orderId);
                sagaOrchestrator.decrementAndFinalize(orderId, "REFUND_FAILED");
            }

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process payment.refund.reply eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private KafkaEnvelope parseEnvelope(String message, Acknowledgment ack) {
        try {
            return objectMapper.readValue(message, KafkaEnvelope.class);
        } catch (Exception ex) {
            log.error("Malformed Kafka message — skipping: {}", ex.getMessage());
            ack.acknowledge(); // poison-pill guard
            return null;
        }
    }

    private boolean isDuplicate(UUID eventId, Acknowledgment ack) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event detected, skipping eventId={}", eventId);
            ack.acknowledge();
            return true;
        }
        return false;
    }

    private void recordProcessed(UUID eventId) {
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .build());
    }

    private UUID extractOrderId(JsonNode payload) {
        return UUID.fromString(payload.get("orderId").asText());
    }
}
