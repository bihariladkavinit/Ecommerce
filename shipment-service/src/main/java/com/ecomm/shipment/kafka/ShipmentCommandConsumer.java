package com.ecomm.shipment.kafka;

import com.ecomm.shipment.entity.*;
import com.ecomm.shipment.kafka.dto.KafkaEnvelope;
import com.ecomm.shipment.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for the Order saga's {@code shipment.create.command}.
 *
 * <h3>What it does</h3>
 * <ol>
 *   <li>Deserialises the platform envelope.</li>
 *   <li>Checks {@code processed_event(event_id)} — skips duplicates (idempotency).</li>
 *   <li>Checks {@code shipments.order_id} — skips if already created (re-delivery guard).</li>
 *   <li>Generates a deterministic tracking number: {@code TRK-{first 8 chars of orderId uppercase}}.</li>
 *   <li>Saves {@link Shipment} (status=CREATED, carrier="STANDARD").</li>
 *   <li>Appends a {@link ShipmentEvent} row for the CREATED transition.</li>
 *   <li>Writes an {@link OutboxEvent} with {@code eventType="shipment.create.reply"} and a
 *       SUCCEEDED payload per the event catalog.</li>
 *   <li>Saves a {@link ProcessedEvent} to record this eventId as handled.</li>
 *   <li>All steps 4–8 are in one {@code @Transactional} method — atomic guarantee.</li>
 *   <li>Manually acknowledges the Kafka offset only after the transaction commits.</li>
 * </ol>
 *
 * <h3>Expected command payload (from event-catalog.md)</h3>
 * <pre>
 * {
 *   "orderId":         "uuid",
 *   "userId":          "uuid",
 *   "shippingAddress": { "line1": "...", "city": "...", "zip": "..." }
 * }
 * </pre>
 *
 * <h3>Published reply payload</h3>
 * <pre>
 * {
 *   "orderId":        "uuid",
 *   "status":         "SUCCEEDED",
 *   "shipmentId":     "uuid",
 *   "trackingNumber": "TRK-A1B2C3D4"
 * }
 * </pre>
 *
 * <h3>Tracking number format</h3>
 * {@code "TRK-" + orderId.toString().replace("-","").substring(0, 8).toUpperCase()}
 * — deterministic, so re-delivery of the same command always produces the same
 * tracking number. The existing-shipment guard (step 3) prevents duplicate rows.
 *
 * <h3>Error handling</h3>
 * Malformed JSON is caught, logged, and the offset acknowledged (poison-pill guard).
 * All other exceptions propagate to Spring Kafka's error handler for retry/DLT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentCommandConsumer {

    private static final String TOPIC_REPLY     = "shipment.create.reply";
    private static final String AGGREGATE_TYPE  = "Shipment";
    private static final String DEFAULT_CARRIER = "STANDARD";

    private final ShipmentRepository      shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final OutboxEventRepository   outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper            objectMapper;

    @KafkaListener(
            topics           = "shipment.create.command",
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onShipmentCreateCommand(@Payload String message, Acknowledgment ack) {
        log.debug("Received shipment.create.command: {}", message);

        // ── 1. Parse envelope ─────────────────────────────────────────
        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;  // poison-pill, already acked

        UUID eventId = envelope.getEventId();

        // ── 2. Idempotency check ──────────────────────────────────────
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate shipment.create.command eventId={} — skipping", eventId);
            ack.acknowledge();
            return;
        }

        try {
            JsonNode payload  = envelope.getPayload();
            UUID     orderId  = UUID.fromString(payload.get("orderId").asText());

            // ── 3. Re-delivery guard ──────────────────────────────────
            // If a Shipment already exists for this order the command was
            // already processed (transaction committed but ack was lost).
            // Re-publish the reply outbox row and ack without touching the DB.
            if (shipmentRepository.findByOrderId(orderId).isPresent()) {
                log.info("Shipment already exists for orderId={} — re-publishing reply", orderId);
                Shipment existing = shipmentRepository.findByOrderId(orderId).get();
                writeOutbox(orderId, existing.getId(), existing.getTrackingNumber());
                processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).build());
                ack.acknowledge();
                return;
            }

            // ── 4. Generate tracking number ───────────────────────────
            // TRK-{first 8 hex chars of orderId, no dashes, uppercase}
            String trackingNumber = "TRK-" +
                    orderId.toString().replace("-", "").substring(0, 8).toUpperCase();

            // ── 5. Create Shipment ────────────────────────────────────
            Shipment shipment = Shipment.builder()
                    .orderId(orderId)
                    .status(ShipmentStatus.CREATED)
                    .carrier(DEFAULT_CARRIER)
                    .trackingNumber(trackingNumber)
                    .build();
            shipment = shipmentRepository.save(shipment);

            // ── 6. Append ShipmentEvent history row ───────────────────
            shipmentEventRepository.save(ShipmentEvent.builder()
                    .shipmentId(shipment.getId())
                    .status(ShipmentStatus.CREATED)
                    .build());

            // ── 7. Write outbox reply ─────────────────────────────────
            writeOutbox(orderId, shipment.getId(), trackingNumber);

            // ── 8. Record idempotency key ─────────────────────────────
            processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).build());

            log.info("Shipment created orderId={} shipmentId={} trackingNumber={}",
                    orderId, shipment.getId(), trackingNumber);

            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process shipment.create.command eventId={}: {}",
                    eventId, ex.getMessage(), ex);
            // Re-throw so Spring Kafka error handler retries / sends to DLT
            throw ex;
        }
    }

    // ── Outbox helper ─────────────────────────────────────────────────

    /**
     * Writes the {@code shipment.create.reply} outbox row.
     *
     * <p>The {@code aggregateId} is set to {@code orderId} so the relay uses it
     * as the Kafka partition key, keeping all saga events for one order on the
     * same partition and preserving message order for the Order Service.
     */
    private void writeOutbox(UUID orderId, UUID shipmentId, String trackingNumber) {
        String payload = buildReplyPayload(orderId, shipmentId, trackingNumber);
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(orderId)       // partition key = orderId
                .eventType(TOPIC_REPLY)
                .payload(payload)
                .published(false)
                .build());
    }

    private String buildReplyPayload(UUID orderId, UUID shipmentId, String trackingNumber) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "orderId",        orderId.toString(),
                    "status",         "SUCCEEDED",
                    "shipmentId",     shipmentId.toString(),
                    "trackingNumber", trackingNumber
            ));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise shipment.create.reply payload", ex);
            return "{}";
        }
    }

    // ── Envelope parser ───────────────────────────────────────────────

    /**
     * Parses the raw JSON string into a {@link KafkaEnvelope}.
     * On failure the message is acknowledged (poison-pill guard) and
     * {@code null} returned so the caller exits immediately.
     */
    private KafkaEnvelope parseEnvelope(String message, Acknowledgment ack) {
        try {
            return objectMapper.readValue(message, KafkaEnvelope.class);
        } catch (Exception ex) {
            log.error("Malformed shipment.create.command message — skipping: {}", ex.getMessage());
            ack.acknowledge();
            return null;
        }
    }
}
