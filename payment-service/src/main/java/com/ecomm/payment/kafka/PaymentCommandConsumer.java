package com.ecomm.payment.kafka;

import com.ecomm.payment.entity.ProcessedEvent;
import com.ecomm.payment.kafka.dto.KafkaEnvelope;
import com.ecomm.payment.repository.ProcessedEventRepository;
import com.ecomm.payment.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Kafka consumer for Order saga payment commands.
 *
 * Listens on two topics:
 *   payment.charge.command - charge the given amount for an order
 *   payment.refund.command - refund a previously charged payment
 *
 * Idempotency:
 *   Before processing, each handler checks processed_event(event_id).
 *   If the record exists the message is a duplicate and is acknowledged
 *   without reprocessing. The ProcessedEvent insert and the business write
 *   happen in the same @Transactional method — a rollback removes the record
 *   too, preventing false deduplication on the next retry.
 *
 * Manual acknowledgement:
 *   ack-mode: RECORD — offset committed only after the DB transaction commits.
 *   A crash between consume and commit re-delivers the message; the idempotency
 *   check handles it safely.
 *
 * Error handling:
 *   Malformed messages are acknowledged immediately (poison-pill guard).
 *   All other exceptions are rethrown for Spring Kafka's error handler to retry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandConsumer {

    private final PaymentService           paymentService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper             objectMapper;

    // ── payment.charge.command ────────────────────────────────────────

    /**
     * Payload: { "orderId": "uuid", "userId": "uuid", "amount": 149.99 }
     */
    @KafkaListener(
            topics = "payment.charge.command",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onChargeCommand(@Payload String message, Acknowledgment ack) {
        log.debug("Received payment.charge.command");

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload = envelope.getPayload();
            UUID       orderId = UUID.fromString(payload.get("orderId").asText());
            BigDecimal amount  = new BigDecimal(payload.get("amount").asText());

            paymentService.charge(orderId, amount);

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

            log.info("payment.charge.command processed orderId={}", orderId);

        } catch (Exception ex) {
            log.error("Failed to process payment.charge.command eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex; // rethrow → Spring Kafka error handler retries
        }
    }

    // ── payment.refund.command ────────────────────────────────────────

    /**
     * Payload: { "orderId": "uuid", "paymentId": "uuid", "amount": 149.99,
     *            "reason": "SHIPMENT_CREATION_FAILED" }
     */
    @KafkaListener(
            topics = "payment.refund.command",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onRefundCommand(@Payload String message, Acknowledgment ack) {
        log.debug("Received payment.refund.command");

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload   = envelope.getPayload();
            UUID       orderId    = UUID.fromString(payload.get("orderId").asText());
            UUID       paymentId  = UUID.fromString(payload.get("paymentId").asText());
            BigDecimal amount     = new BigDecimal(payload.get("amount").asText());
            String     reason     = payload.has("reason")
                    ? payload.get("reason").asText() : "UNSPECIFIED";

            paymentService.refund(orderId, paymentId, amount, reason);

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

            log.info("payment.refund.command processed orderId={} reason={}", orderId, reason);

        } catch (Exception ex) {
            log.error("Failed to process payment.refund.command eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

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
}
