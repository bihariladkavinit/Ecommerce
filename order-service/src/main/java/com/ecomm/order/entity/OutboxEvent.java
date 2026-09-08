package com.ecomm.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transactional outbox table — written in the same DB transaction as any saga
 * state change. The {@link com.ecomm.order.service.OutboxRelayService} polls
 * unpublished rows and publishes them to Kafka.
 *
 * <p>For all order-saga command topics the partition key is {@link #aggregateId}
 * (= {@code orderId}), guaranteeing that all events for one order land on the
 * same Kafka partition and are consumed in order.
 *
 * <p>The {@link #eventType} field doubles as the Kafka topic name, e.g.
 * {@code inventory.reserve.command}, {@code payment.charge.command}, etc.
 */
@Entity
@Table(
        name = "outbox_event",
        indexes = @Index(
                name = "idx_outbox_unpublished",
                columnList = "published, created_at"))
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

    /** e.g. "Order" */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /**
     * The {@code orderId} — used as the Kafka partition key so all events
     * for one order land on the same partition.
     */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /**
     * Also the Kafka topic name, e.g. {@code inventory.reserve.command},
     * {@code payment.charge.command}, {@code order.confirmed}, etc.
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** JSON-serialised business payload (not the full envelope). */
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
