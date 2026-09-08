package com.ecomm.payment.exception;

import org.springframework.http.HttpStatus;

/** HTTP 409 — request conflicts with current state. */
public class ConflictException extends AppException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
