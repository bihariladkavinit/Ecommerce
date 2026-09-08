package com.ecomm.shipment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable audit record of a single status transition on a {@link Shipment}.
 *
 * <p>A row is appended every time the shipment status changes — creation
 * included. This gives a full lifecycle history:
 * <pre>
 *   CREATED   (on saga command)
 *   DISPATCHED (on admin PUT)
 *   DELIVERED  (on admin PUT)
 * </pre>
 *
 * <p>Rows are never updated or deleted — the table is append-only by design.
 */
@Entity
@Table(
        name = "shipment_events",
        indexes = @Index(name = "idx_shipment_events_shipment_id", columnList = "shipment_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ShipmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;
}
