package com.ecomm.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transactional outbox table — written in the same DB transaction as the
 * business change. A background relay ({@link com.ecomm.product.service.OutboxRelayService})
 * polls unpublished rows and publishes them to Kafka.
 *
 * <p>Schema matches the shared outbox shape used across all publishing services
 * in the platform (see db-init.sql).
 */
@Entity
@Table(name = "outbox_event",
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

    /** e.g. "Product" */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /** The product UUID that produced this event. */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /** e.g. "product.created", "product.updated" — also the Kafka topic name. */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * JSON-serialised event payload.
     * Stored as JSONB in Postgres to match the shared outbox schema.
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
