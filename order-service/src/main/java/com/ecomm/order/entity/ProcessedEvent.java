package com.ecomm.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumer-side idempotency record for Kafka reply messages.
 *
 * <p>Before processing any inbound saga reply, the consumer checks whether
 * the message's {@code eventId} (from the platform envelope) already exists
 * in this table. If it does, the message is a duplicate (at-least-once delivery)
 * and is acknowledged without reprocessing.
 *
 * <p>The insert and the business state change happen in the same
 * {@code @Transactional} method, so a rollback removes the idempotency record
 * too — preventing false deduplication on retry.
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

    /** The {@code eventId} from the Kafka message envelope — set explicitly. */
    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;
}
