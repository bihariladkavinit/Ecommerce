package com.ecomm.product.exception;

import org.springframework.http.HttpStatus;

/** 409 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
