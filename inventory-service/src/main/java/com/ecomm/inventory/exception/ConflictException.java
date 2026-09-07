package com.ecomm.inventory.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request conflicts with the current state — for example,
 * when optimistic lock retries are exhausted and the update cannot proceed.
 * Maps to HTTP 409.
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
