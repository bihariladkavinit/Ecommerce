package com.ecomm.shipment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a physical shipment created for an order.
 *
 * <p>The {@code orderId} column has a {@code UNIQUE} constraint (mirroring
 * the DB schema) so it is impossible to double-create a shipment for the
 * same order — even if {@code shipment.create.command} is delivered more
 * than once. The command consumer checks {@code findByOrderId} before
 * inserting to surface a clean idempotency path rather than relying on
 * a DB constraint violation.
 *
 * <p>Tracking number format: {@code TRK-{first 8 chars of orderId uppercase}}.
 * Deterministic — the same orderId always yields the same tracking number,
 * making re-delivery of the create command safe.
 */
@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Mirrors {@code Order.id} — unique per shipment. */
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status;

    /** Carrier name, e.g. "STANDARD". Nullable until dispatched in a real system. */
    @Column(length = 50)
    private String carrier;

    /**
     * Public-facing tracking reference, e.g. {@code TRK-A1B2C3D4}.
     * Set at creation time from the orderId and never changed.
     */
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
