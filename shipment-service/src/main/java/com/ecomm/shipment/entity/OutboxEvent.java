package com.ecomm.shipment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transactional outbox table — written in the same DB transaction as the
 * business change (shipment creation). A background relay
 * ({@link com.ecomm.shipment.service.OutboxRelayService}) polls unpublished
 * rows and publishes them to Kafka.
 *
 * <p>Schema matches the shared outbox shape used across all publishing services
 * in the platform (see db-init.sql). The {@code event_type} column doubles as
 * the Kafka topic name (e.g. {@code shipment.create.reply}).
 *
 * <p>The {@code aggregate_id} is always set to {@code orderId} for shipment
 * events so the relay uses it as the Kafka partition key — keeping all events
 * for one order on the same partition and preserving order for the saga.
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

    /** e.g. "Shipment" */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /**
     * For saga replies this is {@code orderId} — used as the Kafka partition
     * key to keep all events for one order on the same partition.
     */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /**
     * Also the Kafka topic name: {@code shipment.create.reply}.
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * JSON-serialised event payload stored as JSONB in Postgres.
     */
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
