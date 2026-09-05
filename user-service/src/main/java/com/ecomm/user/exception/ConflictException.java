package com.ecomm.user.exception;

import org.springframework.http.HttpStatus;

/** 409 — the request conflicts with current server state (e.g. duplicate email). */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
