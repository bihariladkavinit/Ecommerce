package com.ecomm.payment.service;

import com.ecomm.payment.dto.response.PaymentResponse;
import com.ecomm.payment.entity.*;
import com.ecomm.payment.exception.ResourceNotFoundException;
import com.ecomm.payment.gateway.GatewayResponse;
import com.ecomm.payment.gateway.MockPaymentGateway;
import com.ecomm.payment.mapper.PaymentMapper;
import com.ecomm.payment.repository.OutboxEventRepository;
import com.ecomm.payment.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Core payment business logic — charge and refund operations.
 *
 * Both methods are @Transactional. Every write (Payment row, Transaction row,
 * OutboxEvent row) happens in a single transaction. If the transaction rolls back
 * nothing is persisted and the outbox event is not published — no phantom replies.
 *
 * Double-charge protection has two layers:
 *   1. Application layer: existsByOrderId check at the start of charge().
 *   2. DB layer: UNIQUE constraint on payments.order_id — catches any race condition
 *      that slips past the application check (e.g. two concurrent messages).
 *      DataIntegrityViolationException is mapped to 409 by GlobalExceptionHandler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String TOPIC_CHARGE_REPLY = "payment.charge.reply";
    private static final String TOPIC_REFUND_REPLY  = "payment.refund.reply";
    private static final String AGGREGATE_TYPE      = "Payment";

    private final PaymentRepository    paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final MockPaymentGateway   mockPaymentGateway;
    private final PaymentMapper        paymentMapper;
    private final ObjectMapper         objectMapper;

    // ── Charge ────────────────────────────────────────────────────────

    /**
     * Processes a payment.charge.command.
     *
     * Steps (all in one transaction):
     *   1. Guard against duplicate: return early if payment already exists for this order.
     *   2. Create Payment(PENDING), save.
     *   3. Call mock gateway.
     *   4. Update payment status to COMPLETED or FAILED + set gatewayRef.
     *   5. Save Transaction(CHARGE, SUCCEEDED or FAILED).
     *   6. Write payment.charge.reply outbox event.
     *
     * @param orderId the saga order ID
     * @param amount  the amount to charge
     */
    @Transactional
    public void charge(UUID orderId, BigDecimal amount) {

        // Application-layer duplicate guard (DB constraint is the ultimate backstop)
        if (paymentRepository.existsByOrderId(orderId)) {
            log.warn("charge: payment already exists for orderId={} — skipping", orderId);
            return;
        }

        // Create PENDING payment
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);
        log.debug("charge: created PENDING payment id={} orderId={}", payment.getId(), orderId);

        // Call mock gateway
        GatewayResponse gwResponse = mockPaymentGateway.charge(orderId, amount);

        // Update payment status
        if (gwResponse.isSuccess()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setGatewayRef(gwResponse.getGatewayRef());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        paymentRepository.save(payment);

        // Record transaction
        Transaction tx = Transaction.builder()
                .payment(payment)
                .type(TransactionType.CHARGE)
                .amount(amount)
                .status(gwResponse.isSuccess() ? TransactionStatus.SUCCEEDED : TransactionStatus.FAILED)
                .build();
        payment.getTransactions().add(tx);

        // Write outbox reply
        String payload = buildChargeReplyPayload(orderId, payment.getId(), gwResponse);
        writeOutbox(orderId, TOPIC_CHARGE_REPLY, payload);

        log.info("charge: orderId={} paymentId={} status={} gatewayRef={}",
                orderId, payment.getId(), payment.getStatus(), payment.getGatewayRef());
    }

    // ── Refund ────────────────────────────────────────────────────────

    /**
     * Processes a payment.refund.command.
     *
     * Steps (all in one transaction):
     *   1. Look up Payment by orderId — throw ResourceNotFoundException if missing.
     *   2. Call mock gateway refund.
     *   3. Update payment status to REFUNDED.
     *   4. Save Transaction(REFUND, SUCCEEDED).
     *   5. Write payment.refund.reply SUCCEEDED outbox event.
     *
     * @param orderId   the saga order ID
     * @param paymentId the payment to refund (from the refund command payload)
     * @param amount    the amount to refund
     * @param reason    the compensation reason for logging
     */
    @Transactional
    public void refund(UUID orderId, UUID paymentId, BigDecimal amount, String reason) {

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for orderId: " + orderId));

        log.debug("refund: orderId={} paymentId={} amount={} reason={}", orderId, paymentId, amount, reason);

        // Call mock gateway
        GatewayResponse gwResponse = mockPaymentGateway.refund(orderId, payment.getId(), amount);

        // Update payment status
        payment.setStatus(PaymentStatus.REFUNDED);
        if (gwResponse.getGatewayRef() != null) {
            payment.setGatewayRef(gwResponse.getGatewayRef());
        }
        paymentRepository.save(payment);

        // Record transaction
        Transaction tx = Transaction.builder()
                .payment(payment)
                .type(TransactionType.REFUND)
                .amount(amount)
                .status(TransactionStatus.SUCCEEDED)
                .build();
        payment.getTransactions().add(tx);

        // Write outbox reply
        String payload = buildRefundReplyPayload(orderId);
        writeOutbox(orderId, TOPIC_REFUND_REPLY, payload);

        log.info("refund: orderId={} paymentId={} REFUNDED", orderId, payment.getId());
    }

    // ── Read ──────────────────────────────────────────────────────────

    /**
     * Returns the payment for an order — used by the admin REST endpoint.
     *
     * @throws ResourceNotFoundException if no payment exists for the order
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for orderId: " + orderId));
        return paymentMapper.toResponse(payment);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void writeOutbox(UUID orderId, String topic, String payload) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(orderId)
                .eventType(topic)
                .payload(payload)
                .published(false)
                .build());
    }

    private String buildChargeReplyPayload(UUID orderId, UUID paymentId, GatewayResponse gw) {
        try {
            if (gw.isSuccess()) {
                return objectMapper.writeValueAsString(Map.of(
                        "orderId",    orderId.toString(),
                        "status",     "SUCCEEDED",
                        "paymentId",  paymentId.toString(),
                        "gatewayRef", gw.getGatewayRef()
                ));
            } else {
                return objectMapper.writeValueAsString(Map.of(
                        "orderId", orderId.toString(),
                        "status",  "FAILED",
                        "reason",  gw.getFailureReason() != null ? gw.getFailureReason() : "PAYMENT_FAILED"
                ));
            }
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to build charge reply payload", ex);
        }
    }

    private String buildRefundReplyPayload(UUID orderId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "orderId", orderId.toString(),
                    "status",  "SUCCEEDED"
            ));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to build refund reply payload", ex);
        }
    }
}
