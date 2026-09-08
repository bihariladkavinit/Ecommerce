package com.ecomm.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumer-side idempotency record.
 *
 * <p>Before processing any inbound Kafka message the consumer checks whether
 * the message's {@code eventId} (from the platform envelope) already exists
 * here. If it does the message is a duplicate and is skipped without
 * reprocessing.
 *
 * <p>The insert and the {@link NotificationLog} write happen in the same
 * {@code @Transactional} method so if the transaction rolls back the record
 * is also rolled back — preventing false-deduplication.
 *
 * <p>Notification-service consumes multiple topics, so this single shared
 * table deduplicates all of them (matching the {@code db-init.sql} comment:
 * "notification consumes many topics, single shared dedupe table").
 */
@Entity
@Table(name = "processed_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "eventId")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;
}
