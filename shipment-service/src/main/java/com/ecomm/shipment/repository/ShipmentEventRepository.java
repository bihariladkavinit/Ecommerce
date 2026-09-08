package com.ecomm.shipment.repository;

import com.ecomm.shipment.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link ShipmentEvent}.
 *
 * <p>Rows are append-only — only {@code save} is ever called here.
 * No custom query methods are needed beyond the inherited JPA ones.
 */
@Repository
public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, UUID> {
}
