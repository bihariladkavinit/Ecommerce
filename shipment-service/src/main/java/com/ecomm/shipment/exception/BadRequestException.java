package com.ecomm.shipment.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the caller sends an invalid request.
 * Maps to HTTP 400.
 */
public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
