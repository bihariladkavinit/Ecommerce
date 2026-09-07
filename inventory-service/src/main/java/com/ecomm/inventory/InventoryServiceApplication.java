package com.ecomm.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Inventory Service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Manages per-product stock levels ({@code stock_items}).</li>
 *   <li>Handles saga commands: reserve, confirm, release via Kafka.</li>
 *   <li>Publishes saga replies and domain events via the transactional outbox.</li>
 *   <li>Auto-initialises stock entries when products are created/updated.</li>
 * </ul>
 *
 * <p>{@code @EnableScheduling} activates the outbox relay poller
 * ({@link com.ecomm.inventory.service.OutboxRelayService}).
 */
@SpringBootApplication
@EnableScheduling
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
