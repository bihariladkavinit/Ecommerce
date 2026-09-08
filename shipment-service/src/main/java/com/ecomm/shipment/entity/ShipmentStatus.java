package com.ecomm.shipment.entity;

/**
 * Lifecycle states of a {@link Shipment}.
 *
 * <p>Valid transitions:
 * <pre>
 *   CREATED → DISPATCHED → DELIVERED
 *   CREATED → CANCELLED
 *   DISPATCHED → CANCELLED
 * </pre>
 *
 * Regression attempts (e.g. DELIVERED → DISPATCHED) are rejected by
 * {@link #canTransitionTo(ShipmentStatus)} so the rule lives here,
 * not scattered across service logic.
 */
public enum ShipmentStatus {

    CREATED {
        @Override
        public boolean canTransitionTo(ShipmentStatus next) {
            return next == DISPATCHED || next == CANCELLED;
        }
    },
    DISPATCHED {
        @Override
        public boolean canTransitionTo(ShipmentStatus next) {
            return next == DELIVERED || next == CANCELLED;
        }
    },
    DELIVERED {
        @Override
        public boolean canTransitionTo(ShipmentStatus next) {
            return false; // terminal state
        }
    },
    CANCELLED {
        @Override
        public boolean canTransitionTo(ShipmentStatus next) {
            return false; // terminal state
        }
    };

    /**
     * Returns {@code true} if transitioning from this state to {@code next}
     * is a valid business operation.
     *
     * @param next the desired target state
     * @return whether the transition is allowed
     */
    public abstract boolean canTransitionTo(ShipmentStatus next);
}
