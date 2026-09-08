package com.ecomm.payment.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Deserialisation model for every inbound Kafka message in the platform.
 *
 * Every message on every topic is wrapped in this envelope:
 *   eventId     - unique ID for this message (used for consumer-side deduplication)
 *   eventType   - topic name, e.g. payment.charge.command
 *   sagaId      - always equals orderId for order-saga topics
 *   aggregateId - same as sagaId
 *   timestamp   - ISO-8601 creation time
 *   payload     - business payload as a raw JSON tree
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEnvelope {

    private UUID     eventId;
    private String   eventType;
    private UUID     sagaId;
    private UUID     aggregateId;
    private String   timestamp;
    private JsonNode payload;
}
