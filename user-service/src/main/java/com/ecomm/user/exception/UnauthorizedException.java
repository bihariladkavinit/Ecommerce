package com.ecomm.user.exception;

import org.springframework.http.HttpStatus;

/** 401 — missing, invalid, or expired credentials / token. */
public class UnauthorizedException extends AppException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
