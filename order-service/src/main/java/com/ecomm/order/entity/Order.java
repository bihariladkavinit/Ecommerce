package com.ecomm.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Root aggregate for the order domain.
 *
 * <p>Owns the saga state machine — every saga transition updates
 * {@link #sagaState} (and optionally {@link #status}) in the same DB
 * transaction as the outbox event write, ensuring the two are always consistent.
 *
 * <p>Key fields:
 * <ul>
 *   <li>{@link #shippingAddress} — JSONB snapshot of the address resolved
 *       at order creation time from user-service. Immutable after creation.</li>
 *   <li>{@link #idempotencyKey} — the {@code Idempotency-Key} header value
 *       from {@code POST /orders}. Unique constraint prevents duplicate orders.</li>
 *   <li>{@link #paymentId} — set by the saga orchestrator when
 *       {@code payment.charge.reply SUCCEEDED} is received. Needed by
 *       compensation commands ({@code payment.refund.command}).</li>
 *   <li>{@link #compensationsPending} — tracks how many compensating commands
 *       are still awaiting a reply. When it reaches 0 the order transitions
 *       to CANCELLED.</li>
 * </ul>
 */
@Entity
@Table(
        name = "orders",
        indexes = @Index(name = "idx_orders_user_id", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "saga_state", nullable = false, length = 30)
    private SagaState sagaState;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Full shipping address snapshot serialised as JSONB.
     * Resolved from user-service at order creation time and stored here
     * so later saga steps (shipment.create.command) have the address
     * without calling user-service again.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", columnDefinition = "jsonb")
    private String shippingAddress;

    /** From the {@code Idempotency-Key} header on {@code POST /orders}. */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    /**
     * Set when {@code payment.charge.reply SUCCEEDED} is received.
     * Required by {@code payment.refund.command} in compensation paths.
     * Not in the original DB schema column — stored in the orders table
     * as an additional nullable UUID column.
     */
    @Column(name = "payment_id")
    private UUID paymentId;

    /**
     * Tracks how many compensating commands are awaiting replies.
     * <ul>
     *   <li>0 = no compensation in flight (normal state)</li>
     *   <li>1 = one compensating command sent (e.g. only inventory.release)</li>
     *   <li>2 = two compensating commands sent (inventory.release + payment.refund)</li>
     * </ul>
     * Decremented by each reply consumer. When it reaches 0 in COMPENSATING state,
     * {@code finalizeCancellation} is called.
     */
    @Column(name = "compensations_pending", nullable = false)
    @Builder.Default
    private int compensationsPending = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    // ── Convenience helpers ───────────────────────────────────────────

    /** Returns true if the saga is in a terminal state. */
    public boolean isTerminal() {
        return sagaState == SagaState.CONFIRMED || sagaState == SagaState.CANCELLED;
    }

    /** Returns true if the order can still be cancelled by the user. */
    public boolean isCancellableByUser() {
        return sagaState == SagaState.CREATED || sagaState == SagaState.INVENTORY_RESERVED;
    }
}
