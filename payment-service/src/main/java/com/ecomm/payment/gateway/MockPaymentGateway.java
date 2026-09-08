package com.ecomm.payment.gateway;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulated PSP (Payment Service Provider) gateway.
 *
 * Behaviour is controlled via application.yml:
 *   payment.gateway.mock.failure-rate - probability of a charge being declined (0.0 to 1.0)
 *   payment.gateway.mock.delay-ms     - simulated PSP network latency in milliseconds
 *
 * The Resilience4j annotations wrap every gateway call with the production-realistic
 * pattern described in the system design (section 8):
 *   CircuitBreaker - opens at 50% failure rate, stays open for 15 s
 *   Retry          - 3 attempts with exponential backoff (configured in application.yml)
 *   Bulkhead       - max 10 concurrent gateway calls
 *
 * The fallback fires when the circuit is open, all retries are exhausted, or a
 * TimeLimiter timeout occurs (configured via application.yml timelimiter block).
 * It returns a GATEWAY_UNAVAILABLE failure so the saga can compensate cleanly.
 *
 * Refunds always succeed in this POC — a real PSP would have its own failure modes
 * but keeping refunds reliable avoids compensation deadlocks during development.
 */
@Service
@Slf4j
public class MockPaymentGateway {

    private static final String INSTANCE = "mock-gateway";

    @Value("${payment.gateway.mock.failure-rate:0.0}")
    private double failureRate;

    @Value("${payment.gateway.mock.delay-ms:200}")
    private long delayMs;

    // ── Charge ────────────────────────────────────────────────────────

    /**
     * Simulates a PSP charge request.
     *
     * @param orderId the order being charged (for logging/reference)
     * @param amount  the amount to charge
     * @return GatewayResponse with success flag, gatewayRef (on success), or failureReason
     */
    @CircuitBreaker(name = INSTANCE, fallbackMethod = "chargeFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public GatewayResponse charge(UUID orderId, BigDecimal amount) {
        simulateLatency();

        if (Math.random() < failureRate) {
            log.warn("Mock gateway: CARD_DECLINED orderId={} amount={}", orderId, amount);
            return GatewayResponse.builder()
                    .success(false)
                    .failureReason("CARD_DECLINED")
                    .build();
        }

        String ref = "mock-txn-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        log.info("Mock gateway: CHARGE SUCCEEDED orderId={} amount={} ref={}", orderId, amount, ref);
        return GatewayResponse.builder()
                .success(true)
                .gatewayRef(ref)
                .build();
    }

    @SuppressWarnings("unused") // called by Resilience4j via reflection
    public GatewayResponse chargeFallback(UUID orderId, BigDecimal amount, Throwable t) {
        log.error("Mock gateway CHARGE fallback triggered orderId={} cause={}", orderId, t.getMessage());
        return GatewayResponse.builder()
                .success(false)
                .failureReason("GATEWAY_UNAVAILABLE")
                .build();
    }

    // ── Refund ────────────────────────────────────────────────────────

    /**
     * Simulates a PSP refund request. Always succeeds in this POC.
     *
     * @param orderId   for logging
     * @param paymentId the payment being refunded
     * @param amount    the amount to refund
     * @return GatewayResponse always with success=true
     */
    @CircuitBreaker(name = INSTANCE, fallbackMethod = "refundFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public GatewayResponse refund(UUID orderId, UUID paymentId, BigDecimal amount) {
        simulateLatency();

        String ref = "mock-refund-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        log.info("Mock gateway: REFUND SUCCEEDED orderId={} paymentId={} amount={} ref={}",
                orderId, paymentId, amount, ref);
        return GatewayResponse.builder()
                .success(true)
                .gatewayRef(ref)
                .build();
    }

    @SuppressWarnings("unused")
    public GatewayResponse refundFallback(UUID orderId, UUID paymentId, BigDecimal amount, Throwable t) {
        log.error("Mock gateway REFUND fallback triggered orderId={} cause={}", orderId, t.getMessage());
        return GatewayResponse.builder()
                .success(false)
                .failureReason("GATEWAY_UNAVAILABLE")
                .build();
    }

    // ── Helper ────────────────────────────────────────────────────────

    private void simulateLatency() {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
