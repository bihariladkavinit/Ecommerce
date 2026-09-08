package com.ecomm.order.entity;

/**
 * High-level lifecycle status of an order — stored in {@code orders.status}.
 *
 * <p>Maps the coarse-grained state visible to end-users. The fine-grained
 * saga progress is tracked separately in {@link SagaState}.
 */
public enum OrderStatus {

    /** Order created, saga is in progress. */
    PENDING,

    /** Saga completed successfully — inventory confirmed, payment charged, shipment created. */
    CONFIRMED,

    /** Order was cancelled — either by the user or due to saga failure/compensation. */
    CANCELLED
}
