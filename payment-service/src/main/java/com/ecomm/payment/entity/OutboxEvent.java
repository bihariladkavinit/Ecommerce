package com.ecomm.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transactional outbox — written in the same DB transaction as the business
 * change (charge or refund). The OutboxRelayService polls unpublished rows
 * and publishes them to Kafka.
 *
 * eventType doubles as the Kafka topic name:
 *   payment.charge.reply
 *   payment.refund.reply
 *
 * aggregateId = orderId — used as the Kafka partition key so all events for
 * one order land on the same partition.
 */
@Entity
@Table(
        name = "outbox_event",
        indexes = @Index(name = "idx_outbox_unpublished", columnList = "published, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /** orderId — used as Kafka partition key. */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /** Kafka topic name, e.g. payment.charge.reply. */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
