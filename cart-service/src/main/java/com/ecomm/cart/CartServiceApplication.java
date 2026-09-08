package com.ecomm.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for the Cart Service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Manages active shopping carts stored as Redis hashes ({@code cart:{userId}})
 *       with a 7-day TTL.</li>
 *   <li>Enriches cart items with live product details and availability via
 *       OpenFeign calls to product-service and inventory-service.</li>
 *   <li>Orchestrates checkout by delegating order creation to order-service via Feign.</li>
 *   <li>All Feign calls are wrapped with Resilience4j circuit breakers and retries
 *       configured in {@code application.yml}.</li>
 * </ul>
 *
 * <p>No Kafka, no Postgres — this service's only persistence is Redis.
 */
@SpringBootApplication
@EnableFeignClients
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
