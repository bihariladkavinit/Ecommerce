package com.ecomm.order.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the request conflicts with current state — maps to HTTP 409.
 * Used for: idempotency key reused with a different body, duplicate order, etc.
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
