package com.ecomm.inventory.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all application-level errors.
 *
 * <p>Subclasses pin a specific {@link HttpStatus} so that
 * {@link GlobalExceptionHandler} can return the correct HTTP status code
 * without any additional instanceof checks.
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
