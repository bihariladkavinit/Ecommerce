package com.ecomm.order.entity;

/**
 * Fine-grained saga orchestration state — stored in {@code orders.saga_state}.
 *
 * <p>Transitions (happy path):
 * <pre>
 * CREATED → INVENTORY_RESERVED → PAYMENT_COMPLETED
 *         → INVENTORY_CONFIRMED → SHIPMENT_CREATED → CONFIRMED
 * </pre>
 *
 * <p>Compensation path:
 * <pre>
 * (any state) → COMPENSATING → CANCELLED
 * </pre>
 *
 * <p>Terminal states: {@link #CONFIRMED}, {@link #CANCELLED}.
 */
public enum SagaState {

    /** Order created; inventory.reserve.command published to outbox. */
    CREATED,

    /** inventory.reserve.reply SUCCEEDED received; payment.charge.command published. */
    INVENTORY_RESERVED,

    /** payment.charge.reply SUCCEEDED received; inventory.confirm.command published. */
    PAYMENT_COMPLETED,

    /** inventory.confirm.reply SUCCEEDED received; shipment.create.command published. */
    INVENTORY_CONFIRMED,

    /** shipment.create.reply SUCCEEDED received; order confirmed. */
    SHIPMENT_CREATED,

    /** Terminal success state — order.confirmed published. */
    CONFIRMED,

    /**
     * Compensation in progress — one or more compensating commands
     * (inventory.release / payment.refund) have been published and we are
     * waiting for their replies before transitioning to CANCELLED.
     */
    COMPENSATING,

    /** Terminal failure/cancellation state — order.cancelled published. */
    CANCELLED
}
