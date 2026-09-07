package com.ecomm.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transactional outbox table — written in the same DB transaction as the
 * business change (reservation, confirmation, release). A background relay
 * ({@link com.ecomm.inventory.service.OutboxRelayService}) polls unpublished
 * rows and publishes them to Kafka.
 *
 * <p>Schema matches the shared outbox shape used across all publishing services
 * in the platform (see db-init.sql).
 *
 * <h3>Why outbox for Kafka replies?</h3>
 * Writing the reply directly inside the Kafka listener transaction would create
 * a dual-write problem: the DB commit and the Kafka send are not atomic.
 * Using the outbox guarantees the reply is published <em>if and only if</em>
 * the business transaction committed.
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

    /** e.g. "StockItem", "StockReservation" */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /**
     * For saga replies this is the {@code orderId} — used as the Kafka
     * partition key to keep all events for one order on the same partition.
     */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /**
     * Also the Kafka topic name, e.g. {@code inventory.reserve.reply},
     * {@code inventory.confirm.reply}, {@code inventory.release.reply}.
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * JSON-serialised event payload.
     * Stored as JSONB in Postgres so it can be inspected with SQL queries.
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
