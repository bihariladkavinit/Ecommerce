package com.ecomm.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Payment Service.
 *
 * Responsibilities:
 *   - Consumes payment.charge.command and payment.refund.command from Kafka.
 *   - Processes each command through a mock PSP gateway wrapped in Resilience4j
 *     (CircuitBreaker + Retry + TimeLimiter + Bulkhead).
 *   - Persists charge/refund results to payment_db with a DB-level orderId UNIQUE
 *     constraint as a hard stop against double-charges.
 *   - Publishes payment.charge.reply and payment.refund.reply via the transactional
 *     outbox pattern (outbox relay polls every 500 ms).
 *   - Exposes GET /payments/{orderId} for admin/debugging use.
 *
 * No Feign calls and no Redis — Postgres, Kafka, and Eureka are the only
 * infrastructure dependencies.
 */
@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
