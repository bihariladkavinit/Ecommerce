package com.ecomm.payment.exception;

import org.springframework.http.HttpStatus;
import java.util.UUID;

/**
 * HTTP 409 — thrown when a charge is attempted for an orderId that already
 * has a payment record. Backed by the DB-level UNIQUE constraint on
 * payments.order_id as a hard stop against double-charges.
 */
public class PaymentAlreadyExistsException extends AppException {
    public PaymentAlreadyExistsException(UUID orderId) {
        super(HttpStatus.CONFLICT, "Payment already exists for orderId: " + orderId);
    }
}
