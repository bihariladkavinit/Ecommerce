package com.ecomm.shipment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Shipment Service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Consumes {@code shipment.create.command} from the Order saga and
 *       creates a {@code Shipment} record with a deterministic tracking number.</li>
 *   <li>Publishes {@code shipment.create.reply} via the transactional outbox
 *       so the Order saga can advance and Notification Service can alert the user.</li>
 *   <li>Exposes REST endpoints for shipment lookup (by orderId or tracking number)
 *       and admin-driven status updates (CREATED → DISPATCHED → DELIVERED).</li>
 * </ul>
 *
 * <p>{@code @EnableScheduling} activates the outbox relay poller
 * ({@link com.ecomm.shipment.service.OutboxRelayService}).
 */
@SpringBootApplication
@EnableScheduling
public class ShipmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipmentServiceApplication.class, args);
    }
}
