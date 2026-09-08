package com.ecomm.payment.service;

import com.ecomm.payment.entity.OutboxEvent;
import com.ecomm.payment.repository.OutboxEventRepository;
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
 * Transactional outbox relay — polls unpublished OutboxEvent rows and
 * publishes them to Kafka.
 *
 * Safety against duplicates:
 *   1. PESSIMISTIC_WRITE + SKIP_LOCKED: concurrent instances grab disjoint batches.
 *   2. Idempotent Kafka producer: broker-side dedup on retries.
 *   3. Consumers check processed_event before acting.
 *
 * Partition key = aggregateId (orderId) — keeps all events for one order on
 * the same partition so the Order Service sees them in order.
 *
 * On Kafka failure: the row stays unpublished and is retried next cycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository         outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending =
                outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();

        if (pending.isEmpty()) return;

        log.debug("Outbox relay: processing {} payment event(s)", pending.size());

        for (OutboxEvent event : pending) {
            try {
                String envelope = buildEnvelope(event);

                kafkaTemplate.send(
                        event.getEventType(),               // topic
                        event.getAggregateId().toString(),  // partition key = orderId
                        envelope);

                event.setPublished(true);
                event.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);

                log.debug("Published outbox event id={} type={} aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());

            } catch (Exception ex) {
                log.warn("Failed to publish outbox event id={} type={}: {}",
                        event.getId(), event.getEventType(), ex.getMessage());
                // Leave published=false — self-heals on next cycle.
            }
        }
    }

    private String buildEnvelope(OutboxEvent event) throws JsonProcessingException {
        Object payloadObj;
        try {
            payloadObj = objectMapper.readValue(event.getPayload(), Object.class);
        } catch (JsonProcessingException ex) {
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
