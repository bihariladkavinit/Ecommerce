package com.ecomm.inventory.entity;

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
 * <p>Before processing any inbound Kafka message, the consumer checks whether
 * the message's {@code eventId} (from the platform envelope) already exists in
 * this table. If it does, the message is a duplicate and is skipped without
 * reprocessing.
 *
 * <p>The insert and the business state change happen in the <em>same</em>
 * {@code @Transactional} method, so if the transaction rolls back the
 * idempotency record is also rolled back — preventing false-deduplication.
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

    /**
     * The {@code eventId} from the Kafka message envelope.
     * Set explicitly from the incoming message — no auto-generation.
     */
    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;
}
