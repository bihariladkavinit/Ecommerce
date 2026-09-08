package com.ecomm.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Order Service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Exposes REST endpoints for order creation, retrieval, and cancellation.</li>
 *   <li>Acts as the sole saga orchestrator — drives the inventory→payment→shipment
 *       pipeline via Kafka commands published through the transactional outbox.</li>
 *   <li>Consumes saga reply topics and advances the state machine accordingly.</li>
 *   <li>Enforces REST-level idempotency via the {@code Idempotency-Key} header.</li>
 *   <li>Runs a scheduled outbox relay (500 ms) and a saga timeout reaper (30 s).</li>
 * </ul>
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
