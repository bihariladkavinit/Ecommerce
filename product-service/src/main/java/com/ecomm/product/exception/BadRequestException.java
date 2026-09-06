package com.ecomm.product.exception;

import org.springframework.http.HttpStatus;

/** 400 */
public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
