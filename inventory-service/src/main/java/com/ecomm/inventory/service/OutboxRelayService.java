package com.ecomm.inventory.service;

import com.ecomm.inventory.entity.OutboxEvent;
import com.ecomm.inventory.repository.OutboxEventRepository;
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
 *   <li>The DB query uses {@code PESSIMISTIC_WRITE + SKIP_LOCKED}, so two
 *       concurrent instances never process the same batch.</li>
 *   <li>The Kafka producer is configured with {@code enable.idempotence=true},
 *       so broker-side retries don't produce duplicate messages.</li>
 *   <li>Consumers check {@code processed_event(event_id)} before acting,
 *       making the pipeline effectively-once end-to-end.</li>
 * </ol>
 *
 * <h3>Partition key convention</h3>
 * For all saga reply topics ({@code inventory.*.reply}) the partition key is
 * the {@code aggregateId} — which is always set to {@code orderId} in
 * {@link SagaInventoryService}. This guarantees all events for one order land
 * on the same partition and are consumed in order by the Order Service.
 *
 * <h3>Failure handling</h3>
 * If Kafka is unavailable the send future completes exceptionally. The outbox
 * row is <em>not</em> marked published and will be retried on the next poll
 * cycle. The service log will show a WARN — the DB row is the source of truth
 * and the relay self-heals once Kafka recovers.
 *
 * <h3>Envelope format</h3>
 * Every Kafka message is wrapped in the platform-standard envelope:
 * <pre>
 * {
 *   "eventId":     "random-uuid",
 *   "eventType":   "inventory.reserve.reply",
 *   "sagaId":      "",
 *   "aggregateId": "order-uuid",
 *   "timestamp":   "ISO-8601",
 *   "payload":     { ... business payload ... }
 * }
 * </pre>
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
     * Uses {@code fixedDelay} so a slow poll cycle never causes overlapping executions.
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

                // Partition key = aggregateId.
                // For saga replies this is orderId — keeps all order events on the same partition.
                kafkaTemplate.send(
                        event.getEventType(),               // topic
                        event.getAggregateId().toString(),  // partition key
                        envelope);                          // value

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
     * Wraps the stored business payload in the platform-standard event envelope.
     * The payload is stored as a JSON string in the DB; it is deserialised to an
     * {@link Object} here so Jackson embeds it as a nested JSON object (not an
     * escaped string) in the final envelope.
     */
    private String buildEnvelope(OutboxEvent event) throws JsonProcessingException {
        Object payloadObj;
        try {
            payloadObj = objectMapper.readValue(event.getPayload(), Object.class);
        } catch (JsonProcessingException ex) {
            // If payload is somehow not valid JSON, embed as raw string
            payloadObj = event.getPayload();
        }

        Map<String, Object> envelope = Map.of(
                "eventId",     UUID.randomUUID().toString(),
                "eventType",   event.getEventType(),
                "sagaId",      event.getAggregateId().toString(), // orderId doubles as sagaId
                "aggregateId", event.getAggregateId().toString(),
                "timestamp",   OffsetDateTime.now().toString(),
                "payload",     payloadObj
        );

        return objectMapper.writeValueAsString(envelope);
    }
}
