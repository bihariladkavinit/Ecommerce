package com.ecomm.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumer-side idempotency record for Kafka command messages.
 *
 * Before processing any inbound Kafka message the consumer checks whether
 * the message's eventId already exists here. If it does, the message is a
 * duplicate and is acknowledged without reprocessing. The insert and the
 * business write happen in the same transaction — a rollback also removes
 * the record, preventing false deduplication on retry.
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
