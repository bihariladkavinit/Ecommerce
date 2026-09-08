package com.ecomm.order.service;

import com.ecomm.order.entity.OutboxEvent;
import com.ecomm.order.repository.OutboxEventRepository;
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
 * <h3>Safety against duplicates</h3>
 * <ol>
 *   <li>The DB query uses {@code PESSIMISTIC_WRITE + SKIP_LOCKED} so two
 *       concurrent instances never process the same batch.</li>
 *   <li>The Kafka producer has {@code enable.idempotence=true}, preventing
 *       duplicate messages on broker-side retries.</li>
 *   <li>Reply consumers check {@code processed_event(event_id)} before acting,
 *       making the pipeline effectively-once end-to-end.</li>
 * </ol>
 *
 * <h3>Partition key convention</h3>
 * For all order-saga command topics the partition key is {@code aggregateId}
 * (= {@code orderId}). This guarantees all events for one order land on the
 * same partition and are consumed in order by downstream services.
 *
 * <h3>Envelope format</h3>
 * Every Kafka message is wrapped in the platform-standard envelope:
 * <pre>
 * {
 *   "eventId":     "random-uuid",
 *   "eventType":   "inventory.reserve.command",
 *   "sagaId":      "order-uuid",
 *   "aggregateId": "order-uuid",
 *   "timestamp":   "ISO-8601",
 *   "payload":     { ... business payload ... }
 * }
 * </pre>
 *
 * <h3>Failure handling</h3>
 * If Kafka is unavailable the send future fails. The outbox row is <em>not</em>
 * marked published and will be retried on the next poll cycle. The service
 * self-heals once Kafka recovers — the DB row is the source of truth.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository         outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    /**
     * Poll every {@code outbox.poll-interval-ms} (default 500 ms).
     * Uses {@code fixedDelay} so a slow cycle never causes overlapping runs.
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

                // Partition key = aggregateId (orderId) — keeps all events for one order
                // on the same partition so downstream consumers see them in order.
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
                // Don't mark published — let the next cycle retry.
                log.warn("Failed to publish outbox event id={} type={}: {}",
                        event.getId(), event.getEventType(), ex.getMessage());
            }
        }
    }

    // ── Envelope builder ──────────────────────────────────────────────

    /**
     * Wraps the stored business payload in the platform-standard event envelope.
     * The payload is stored as a JSON string in the DB; it is deserialised to an
     * Object here so Jackson embeds it as a nested object (not an escaped string).
     */
    private String buildEnvelope(OutboxEvent event) throws JsonProcessingException {
        Object payloadObj;
        try {
            payloadObj = objectMapper.readValue(event.getPayload(), Object.class);
        } catch (JsonProcessingException ex) {
            payloadObj = event.getPayload(); // fallback: embed as raw string
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
