package com.ecomm.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable audit record of a single notification send attempt.
 *
 * <p>One row is written per Kafka event that triggers a notification,
 * regardless of whether the send succeeded or failed. This provides a
 * complete history that can be queried by the admin endpoint
 * ({@code GET /notifications?userId=}).
 *
 * <p>The {@code payload} column stores the raw JSON payload from the
 * originating Kafka event, stored as {@code JSONB} in Postgres so it can
 * be inspected with SQL queries for debugging without parsing the log lines.
 *
 * <p>Schema matches {@code notification_log} in {@code db-init.sql}:
 * <pre>
 *   id         UUID PK
 *   type       VARCHAR(20)   EMAIL | SMS
 *   recipient  VARCHAR(255)  email address or phone number
 *   subject    VARCHAR(255)  nullable (no subject for SMS)
 *   payload    JSONB         nullable — raw event payload for debugging
 *   status     VARCHAR(20)   SENT | FAILED
 *   created_at TIMESTAMPTZ
 * </pre>
 */
@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(length = 255)
    private String subject;

    /**
     * Raw JSON payload from the Kafka event — stored as JSONB for
     * SQL-level inspection. Nullable when not provided by the consumer.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
