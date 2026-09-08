package com.ecomm.order.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Deserialisation model for every inbound Kafka message in the platform.
 *
 * <p>Every message on every topic is wrapped in this envelope:
 * <pre>
 * {
 *   "eventId":     "uuid",
 *   "eventType":   "inventory.reserve.reply",
 *   "sagaId":      "order-uuid",
 *   "aggregateId": "order-uuid",
 *   "timestamp":   "ISO-8601",
 *   "payload":     { ... business payload ... }
 * }
 * </pre>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures forward-compatible
 * deserialization — extra fields added by future producers don't break this consumer.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEnvelope {

    /** Unique ID for this specific message — used for consumer-side deduplication. */
    private UUID eventId;

    /** The type/topic of this event, e.g. {@code inventory.reserve.reply}. */
    private String eventType;

    /**
     * The saga ID — always equals {@code orderId} for all order-saga topics.
     * Used to correlate this reply back to the originating saga instance.
     */
    private UUID sagaId;

    /** The aggregate ID — same as {@code sagaId} for order-saga topics. */
    private UUID aggregateId;

    /** ISO-8601 timestamp of when the message was created. */
    private String timestamp;

    /**
     * The business payload as a raw JSON tree.
     * Consumers navigate this with {@code payload.get("fieldName").asText()} etc.
     */
    private JsonNode payload;
}
