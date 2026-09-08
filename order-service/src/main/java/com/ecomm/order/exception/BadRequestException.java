package com.ecomm.order.exception;

import org.springframework.http.HttpStatus;

/** Thrown when the request is semantically invalid — maps to HTTP 400. */
public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
