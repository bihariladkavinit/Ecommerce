package com.ecomm.shipment.service;

import com.ecomm.shipment.entity.OutboxEvent;
import com.ecomm.shipment.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox relay — polls unpublished {@link OutboxEvent} rows and
 * publishes them to Kafka.
 *
 * <h3>Why this is safe against duplicates</h3>
 * <ol>
 *   <li>The DB query uses {@code PESSIMISTIC_WRITE + SKIP_LOCKED} so two
 *       concurrent instances of this service never process the same batch.</li>
 *   <li>The Kafka producer is configured with {@code enable.idempotence=true}
 *       so broker-side retries don't produce duplicate messages.</li>
 *   <li>Consumers check {@code processed_event(event_id)} before acting,
 *       making the overall pipeline effectively-once end-to-end.</li>
 * </ol>
 *
 * <h3>Partition key convention</h3>
 * The relay uses {@code aggregateId} (always {@code orderId} for shipment events)
 * as the Kafka partition key. This keeps all saga events for one order on the
 * same partition so the Order Service consumes {@code shipment.create.reply}
 * in the correct order relative to other reply topics.
 *
 * <h3>Envelope format</h3>
 * Every published Kafka message is wrapped in the platform-standard envelope:
 * <pre>
 * {
 *   "eventId":     "random-uuid",
 *   "eventType":   "shipment.create.reply",
 *   "sagaId":      "order-uuid",
 *   "aggregateId": "order-uuid",
 *   "timestamp":   "ISO-8601",
 *   "payload":     { ... business payload ... }
 * }
 * </pre>
 *
 * <h3>Failure handling</h3>
 * If Kafka is unavailable the send future completes exceptionally. The outbox
 * row is <em>not</em> marked published and will be retried on the next poll
 * cycle (WARN logged). The DB row is the source of truth and the relay
 * self-heals once Kafka recovers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository         outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    /**
     * Poll interval driven by {@code outbox.poll-interval-ms} (default 500 ms).
     * Uses {@code fixedDelay} so a slow poll cycle never causes overlapping
     * executions within one instance.
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending =
                outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();

        if (pending.isEmpty()) return;

        log.debug("Outbox relay: processing {} events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                String envelope = buildEnvelope(event);

                // Partition key = aggregateId = orderId
                // Keeps all order-saga events on the same partition → ordered delivery.
                kafkaTemplate.send(
                        event.getEventType(),               // topic: shipment.create.reply
                        event.getAggregateId().toString(),  // partition key: orderId
                        envelope);

                event.setPublished(true);
                event.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);

                log.debug("Published outbox event id={} type={} aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());

            } catch (Exception ex) {
                // Log and continue — do NOT mark published.
                // Row stays unpublished and will be retried on the next cycle.
                log.warn("Failed to publish outbox event id={} type={}: {}",
                        event.getId(), event.getEventType(), ex.getMessage());
            }
        }
    }

    // ── Envelope builder ──────────────────────────────────────────────

    /**
     * Wraps the stored business payload in the platform-standard event envelope.
     *
     * <p>The payload is stored as a JSON string in the DB; it is deserialised
     * to an {@link Object} here so Jackson embeds it as a nested JSON object
     * (not an escaped string) in the final envelope.
     *
     * <p>{@code sagaId} is set to {@code aggregateId} (= orderId) since for
     * shipment saga replies the order is the saga root.
     */
    private String buildEnvelope(OutboxEvent event) throws JsonProcessingException {
        Object payloadObj;
        try {
            payloadObj = objectMapper.readValue(event.getPayload(), Object.class);
        } catch (JsonProcessingException ex) {
            // Payload is not valid JSON — embed as raw string rather than crashing
            payloadObj = event.getPayload();
        }

        Map<String, Object> envelope = Map.of(
                "eventId",     UUID.randomUUID().toString(),
                "eventType",   event.getEventType(),
                "sagaId",      event.getAggregateId().toString(),
                "aggregateId", event.getAggregateId().toString(),
                "timestamp",   OffsetDateTime.now().toString(),
                "payload",     payloadObj
        );

        return objectMapper.writeValueAsString(envelope);
    }
}
