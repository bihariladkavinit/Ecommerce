package com.ecomm.shipment.repository;

import com.ecomm.shipment.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link Shipment}.
 *
 * <p>The two custom finders support the REST tracking endpoints:
 * <ul>
 *   <li>{@link #findByOrderId} — used by {@code GET /shipments/{orderId}}</li>
 *   <li>{@link #findByTrackingNumber} — used by {@code GET /shipments/track/{trackingNumber}}</li>
 * </ul>
 * Both are also used by the command consumer for idempotency checks before
 * creating a new shipment.
 */
@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByOrderId(UUID orderId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);
}
