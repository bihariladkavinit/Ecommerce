package com.ecomm.order.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user attempts to cancel an order that has progressed past
 * the cancellable window (i.e. payment has already been charged) — maps to HTTP 409.
 */
public class OrderNotCancellableException extends AppException {

    public OrderNotCancellableException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
