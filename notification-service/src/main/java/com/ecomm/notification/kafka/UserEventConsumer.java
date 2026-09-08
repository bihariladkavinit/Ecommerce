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

/**
 * Consumes {@code user.registered} events and sends a welcome email.
 *
 * <h3>Expected payload (from event-catalog.md)</h3>
 * <pre>
 * { "userId": "uuid", "email": "user@example.com", "firstName": "John", "lastName": "Doe" }
 * </pre>
 *
 * <h3>Notification sent</h3>
 * <pre>
 *   To      : john.doe@example.com
 *   Subject : Welcome to our store!
 *   Body    : Hi John, welcome! Your account has been created successfully.
 * </pre>
 *
 * <h3>Idempotency</h3>
 * Checks {@code processed_event(event_id)} before delegating to
 * {@link NotificationService#sendAndLog} which writes both the
 * {@code notification_log} row and the {@code processed_event} row
 * atomically in one {@code @Transactional} call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final NotificationService      notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper             objectMapper;

    @KafkaListener(
            topics           = "user.registered",
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onUserRegistered(@Payload String message, Acknowledgment ack) {
        log.debug("Received user.registered: {}", message);

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            JsonNode payload   = envelope.getPayload();
            String   email     = payload.get("email").asText();
            String   firstName = payload.has("firstName") ? payload.get("firstName").asText() : "there";

            String subject = "Welcome to our store!";
            String body    = String.format(
                    "Hi %s, welcome! Your account has been created successfully. " +
                    "Start browsing our catalogue at http://localhost:8080/api/v1/products.",
                    firstName);

            notificationService.sendAndLog(
                    envelope.getEventId(),
                    NotificationType.EMAIL,
                    email,
                    subject,
                    body,
                    message);

            ack.acknowledge();
            log.info("Welcome notification queued for userId={} email={}", payload.get("userId"), email);

        } catch (Exception ex) {
            log.error("Failed to handle user.registered eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private KafkaEnvelope parseEnvelope(String message, Acknowledgment ack) {
        try {
            return objectMapper.readValue(message, KafkaEnvelope.class);
        } catch (Exception ex) {
            log.error("Malformed user.registered message — skipping: {}", ex.getMessage());
            ack.acknowledge();
            return null;
        }
    }

    private boolean isDuplicate(java.util.UUID eventId, Acknowledgment ack) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate user.registered eventId={} — skipping", eventId);
            ack.acknowledge();
            return true;
        }
        return false;
    }
}
