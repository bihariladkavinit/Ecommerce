package com.ecomm.shipment.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request conflicts with current state — primarily used for
 * invalid shipment status transitions (e.g. DELIVERED → DISPATCHED).
 * Maps to HTTP 409.
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
