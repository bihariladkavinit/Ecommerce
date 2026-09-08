package com.ecomm.notification.kafka;

import com.ecomm.notification.entity.NotificationType;
import com.ecomm.notification.kafka.dto.KafkaEnvelope;
import com.ecomm.notification.repository.ProcessedEventRepository;
import com.ecomm.notification.service.NotificationService;
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
 * Consumes order-lifecycle and shipment events and dispatches notification emails.
 *
 * <p>Handles three topics:
 * <ul>
 *   <li>{@code order.confirmed}       — order confirmation email</li>
 *   <li>{@code order.cancelled}       — order cancellation email</li>
 *   <li>{@code shipment.create.reply} — shipment dispatched email with tracking number</li>
 * </ul>
 *
 * <h3>Recipient resolution</h3>
 * The {@code order.confirmed} and {@code order.cancelled} payloads carry a
 * {@code userId} (UUID), not an email address. In a production system this
 * would be resolved to an email via a Feign call to User Service. For this
 * implementation the {@code userId.toString()} is used as a stand-in recipient
 * so the notification log is populated and the mock sender fires — the pattern
 * is complete and the Feign lookup is the only thing left to wire.
 *
 * <p>The {@code shipment.create.reply} payload doesn't include a user email
 * either, so the same userId stand-in is used for consistency.
 *
 * <h3>Idempotency</h3>
 * Same pattern as {@link UserEventConsumer}: check {@code processed_event}
 * before acting; {@link NotificationService#sendAndLog} writes both the log
 * and the idempotency record atomically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationService      notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper             objectMapper;

    // ─────────────────────────────────────────────────────────────────
    // order.confirmed
    // ─────────────────────────────────────────────────────────────────

    /**
     * Payload (from event-catalog.md):
     * <pre>{ "orderId": "uuid", "userId": "uuid", "totalAmount": 149.99 }</pre>
     */
    @KafkaListener(
            topics           = "order.confirmed",
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onOrderConfirmed(@Payload String message, Acknowledgment ack) {
        log.debug("Received order.confirmed: {}", message);

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload     = envelope.getPayload();
            String   orderId     = payload.get("orderId").asText();
            String   userId      = payload.get("userId").asText();
            String   totalAmount = payload.has("totalAmount")
                    ? payload.get("totalAmount").asText() : "N/A";

            // Recipient: userId used as stand-in — would be resolved to email via Feign in production
            String recipient = resolveRecipient(userId);
            String subject   = "Order Confirmed — #" + shortenId(orderId);
            String body      = String.format(
                    "Your order #%s has been confirmed! Total amount: £%s. " +
                    "Track your order at http://localhost:8080/api/v1/orders/%s",
                    shortenId(orderId), totalAmount, orderId);

            notificationService.sendAndLog(
                    envelope.getEventId(), NotificationType.EMAIL,
                    recipient, subject, body, message);

            ack.acknowledge();
            log.info("Order confirmation notification sent orderId={}", orderId);

        } catch (Exception ex) {
            log.error("Failed to handle order.confirmed eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // order.cancelled
    // ─────────────────────────────────────────────────────────────────

    /**
     * Payload (from event-catalog.md):
     * <pre>{ "orderId": "uuid", "userId": "uuid", "reason": "PAYMENT_FAILED" }</pre>
     */
    @KafkaListener(
            topics           = "order.cancelled",
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onOrderCancelled(@Payload String message, Acknowledgment ack) {
        log.debug("Received order.cancelled: {}", message);

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload = envelope.getPayload();
            String   orderId = payload.get("orderId").asText();
            String   userId  = payload.get("userId").asText();
            String   reason  = payload.has("reason") ? payload.get("reason").asText() : "UNSPECIFIED";

            String recipient = resolveRecipient(userId);
            String subject   = "Order Cancelled — #" + shortenId(orderId);
            String body      = String.format(
                    "Unfortunately your order #%s has been cancelled. Reason: %s. " +
                    "If you have any questions please contact support.",
                    shortenId(orderId), reason.replace("_", " "));

            notificationService.sendAndLog(
                    envelope.getEventId(), NotificationType.EMAIL,
                    recipient, subject, body, message);

            ack.acknowledge();
            log.info("Order cancellation notification sent orderId={} reason={}", orderId, reason);

        } catch (Exception ex) {
            log.error("Failed to handle order.cancelled eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // shipment.create.reply
    // ─────────────────────────────────────────────────────────────────

    /**
     * Payload (from event-catalog.md):
     * <pre>{ "orderId": "uuid", "status": "SUCCEEDED", "shipmentId": "uuid", "trackingNumber": "TRK-..." }</pre>
     *
     * <p>Only SUCCEEDED replies trigger a notification — FAILED replies indicate
     * the saga is compensating and the order.cancelled event will follow.
     */
    @KafkaListener(
            topics           = "shipment.create.reply",
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onShipmentCreated(@Payload String message, Acknowledgment ack) {
        log.debug("Received shipment.create.reply: {}", message);

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload        = envelope.getPayload();
            String   status         = payload.has("status") ? payload.get("status").asText() : "";
            String   orderId        = payload.get("orderId").asText();
            String   trackingNumber = payload.has("trackingNumber")
                    ? payload.get("trackingNumber").asText() : "N/A";

            // Only notify on successful shipment creation
            if (!"SUCCEEDED".equalsIgnoreCase(status)) {
                log.debug("shipment.create.reply status={} — no notification sent orderId={}",
                        status, orderId);
                processedEventRepository.save(
                        com.ecomm.notification.entity.ProcessedEvent.builder()
                                .eventId(envelope.getEventId())
                                .build());
                ack.acknowledge();
                return;
            }

            // Recipient: orderId used as stand-in for userId-based email lookup
            // The shipment.create.reply payload doesn't include userId/email directly.
            // In production: fetch user email via Order Service Feign client.
            String recipient = "order-" + shortenId(orderId) + "@notification.internal";
            String subject   = "Your order has been shipped!";
            String body      = String.format(
                    "Great news! Your order #%s is on its way. " +
                    "Track your shipment with tracking number: %s. " +
                    "Visit http://localhost:8080/api/v1/shipments/track/%s for live updates.",
                    shortenId(orderId), trackingNumber, trackingNumber);

            notificationService.sendAndLog(
                    envelope.getEventId(), NotificationType.EMAIL,
                    recipient, subject, body, message);

            ack.acknowledge();
            log.info("Shipment notification sent orderId={} trackingNumber={}",
                    orderId, trackingNumber);

        } catch (Exception ex) {
            log.error("Failed to handle shipment.create.reply eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Parses the raw JSON string into a {@link KafkaEnvelope}.
     * On failure acknowledges (poison-pill guard) and returns {@code null}.
     */
    private KafkaEnvelope parseEnvelope(String message, Acknowledgment ack) {
        try {
            return objectMapper.readValue(message, KafkaEnvelope.class);
        } catch (Exception ex) {
            log.error("Malformed order/shipment event message — skipping: {}", ex.getMessage());
            ack.acknowledge();
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

    /**
     * Stand-in recipient derivation from userId.
     *
     * <p>In production this would call User Service (via Feign) to resolve
     * the UUID to a real email address. Here we format a recognisable string
     * so the notification_log is human-readable in dev.
     */
    private String resolveRecipient(String userId) {
        return "user-" + shortenId(userId) + "@notification.internal";
    }

    /** Returns the first 8 characters of a UUID string (no dashes) for compact display. */
    private String shortenId(String id) {
        return id.replace("-", "").substring(0, Math.min(8, id.replace("-", "").length())).toUpperCase();
    }
}
