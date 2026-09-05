package com.ecomm.user.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all application-level errors.
 * Every subclass maps to a specific HTTP status, keeping
 * the GlobalExceptionHandler concise.
 */
@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public AppException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
