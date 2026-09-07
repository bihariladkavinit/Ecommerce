package com.ecomm.inventory.repository;

import com.ecomm.inventory.entity.ReservationStatus;
import com.ecomm.inventory.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link StockReservation}.
 *
 * <p>The main query used by the saga confirm/release handlers is
 * {@link #findByOrderIdAndStatus}, which loads all reservations for a given
 * order that are still in a specific state (e.g. RESERVED).
 */
@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    /**
     * Returns all reservations for an order in a given status.
     * Used by confirm (status=RESERVED → CONFIRMED) and
     * release (status=RESERVED → RELEASED) flows.
     */
    List<StockReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);
}
