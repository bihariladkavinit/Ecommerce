package com.ecomm.notification.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.UUID;

/**
 * Platform-standard Kafka message envelope.
 *
 * <p>Every message on every topic in this platform is wrapped in this shape:
 * <pre>
 * {
 *   "eventId":     "uuid",
 *   "eventType":   "user.registered",
 *   "sagaId":      "uuid",
 *   "aggregateId": "uuid",
 *   "timestamp":   "ISO-8601",
 *   "payload":     { ... }
 * }
 * </pre>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures forward
 * compatibility if new envelope fields are added to the platform later.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEnvelope {

    private UUID      eventId;
    private String    eventType;
    private UUID      sagaId;
    private UUID      aggregateId;
    private String    timestamp;

    /**
     * Raw JSON payload node — each consumer extracts what it needs.
     * Using {@link JsonNode} avoids coupling the envelope to topic-specific DTOs.
     */
    private JsonNode payload;
}
