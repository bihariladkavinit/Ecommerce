package com.ecomm.cart.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the request is semantically invalid — maps to HTTP 400.
 *
 * <p>Examples: adding a quantity ≤ 0, attempting to add an inactive product.
 */
public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
