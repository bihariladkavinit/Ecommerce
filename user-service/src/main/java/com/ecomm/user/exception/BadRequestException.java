package com.ecomm.user.exception;

import org.springframework.http.HttpStatus;

/** 400 — the request is malformed or contains invalid business logic. */
public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
