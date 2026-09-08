package com.ecomm.notification.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all application-level errors in notification-service.
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
