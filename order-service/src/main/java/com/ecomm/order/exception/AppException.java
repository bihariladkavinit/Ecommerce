package com.ecomm.order.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all application-level errors in order-service.
 * Subclasses pin a specific {@link HttpStatus} so the global handler
 * can return the correct code without instanceof checks.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
