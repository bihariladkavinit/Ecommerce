package com.ecomm.inventory.entity;

/**
 * Lifecycle states of a {@link StockReservation}.
 *
 * <ul>
 *   <li>{@code RESERVED}  — stock has been earmarked for an order; available_qty is reduced.</li>
 *   <li>{@code CONFIRMED} — reservation converted to a real deduction; reserved_qty is reduced.</li>
 *   <li>{@code RELEASED}  — reservation cancelled (e.g. payment failed); stock returned to available.</li>
 * </ul>
 */
public enum ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED
}
