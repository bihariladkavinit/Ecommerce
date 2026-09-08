package com.ecomm.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit trail of every {@link OrderStatus} change on an order.
 *
 * <p>Written whenever the high-level {@code status} field changes (PENDING → CONFIRMED,
 * PENDING → CANCELLED). Provides a human-readable history for debugging and
 * customer support.
 */
@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The order this history entry belongs to (UUID only — no FK object). */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 30)
    private String status;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;
}
