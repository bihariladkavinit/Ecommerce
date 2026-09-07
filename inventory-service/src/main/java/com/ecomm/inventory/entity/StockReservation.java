package com.ecomm.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Records a per-item reservation created during an Order saga's reserve step.
 *
 * <p>One {@link StockReservation} row is created per {@code (orderId, productId)}
 * pair. Its {@link ReservationStatus} transitions:
 * <pre>
 *   RESERVED → CONFIRMED  (on inventory.confirm.command)
 *   RESERVED → RELEASED   (on inventory.release.command, i.e. compensation)
 * </pre>
 *
 * <p>The {@code order_id} index ({@code idx_reservations_order_id}) makes it
 * fast to load all reservations for a given order during confirm/release.
 */
@Entity
@Table(
        name = "stock_reservations",
        indexes = @Index(name = "idx_reservations_order_id", columnList = "order_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** Number of units reserved. Must be > 0 (CHECK in DB). */
    @Column(nullable = false)
    private int qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
