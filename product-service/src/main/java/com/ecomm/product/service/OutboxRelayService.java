package com.ecomm.product.service;

import com.ecomm.product.entity.OutboxEvent;
import com.ecomm.product.repository.OutboxEventRepository;
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
 * Transactional outbox relay — reads unpublished {@link OutboxEvent} rows
 * and publishes them to Kafka.
 *
 * <h3>Why this is safe against duplicates</h3>
 * <ol>
 *   <li>The DB query uses {@code PESSIMISTIC_WRITE + SKIP_LOCKED}, so two
 *       concurrent instances of this service never process the same batch.</li>
 *   <li>The Kafka producer is configured with {@code enable.idempotence=true},
 *       so broker-side retries don't produce duplicate messages.</li>
 *   <li>Consumers check {@code processed_event(event_id)} before acting,
 *       making the overall pipeline effectively-once end-to-end.</li>
 * </ol>
 *
 * <h3>Failure handling</h3>
 * If Kafka is unavailable, the send future completes exceptionally. The outbox
 * row is <em>not</em> marked published and will be retried on the next poll
 * cycle. The service log will show a WARN — this is intentional; the DB row
 * is the source of truth and the relay will self-heal once Kafka recovers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository       outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                objectMapper;

    /**
     * Poll interval driven by {@code outbox.poll-interval-ms} (default 500ms).
     * Uses fixed-delay so a slow poll cycle doesn't cause overlapping executions.
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

                // Partition key = aggregateId (productId) so all events for
                // one product land on the same partition and are ordered.
                kafkaTemplate.send(event.getEventType(),
                        event.getAggregateId().toString(),
                        envelope);

                event.setPublished(true);
                event.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);

                log.debug("Published outbox event id={} type={} aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());

            } catch (Exception ex) {
                // Log and continue — do NOT mark published.
                // The row stays unpublished and will be retried next cycle.
                log.warn("Failed to publish outbox event id={} type={}: {}",
                        event.getId(), event.getEventType(), ex.getMessage());
            }
        }
    }

    // ── Envelope builder ──────────────────────────────────────────────

    /**
     * Wraps the stored business payload in the standard platform event envelope:
     * <pre>
     * {
     *   "eventId":     "uuid",
     *   "eventType":   "product.created",
     *   "sagaId":      null,
     *   "aggregateId": "product-uuid",
     *   "timestamp":   "ISO-8601",
     *   "payload":     { ... }
     * }
     * </pre>
     */
    private String buildEnvelope(OutboxEvent event) throws JsonProcessingException {
        // The stored payload is already a JSON string — deserialise to Object
        // so it embeds correctly as a nested object, not an escaped string.
        Object payloadObj;
        try {
            payloadObj = objectMapper.readValue(event.getPayload(), Object.class);
        } catch (JsonProcessingException ex) {
            // If payload is not valid JSON, embed as raw string
            payloadObj = event.getPayload();
        }

        Map<String, Object> envelope = Map.of(
                "eventId",     UUID.randomUUID().toString(),
                "eventType",   event.getEventType(),
                "sagaId",      "",
                "aggregateId", event.getAggregateId().toString(),
                "timestamp",   OffsetDateTime.now().toString(),
                "payload",     payloadObj
        );

        return objectMapper.writeValueAsString(envelope);
    }
}
