package com.ecomm.cart.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when checkout is attempted on an empty cart — maps to HTTP 400.
 *
 * <p>A cart is considered empty when it has no items stored in Redis
 * (the key either doesn't exist or the hash has no entries).
 */
public class CartEmptyException extends AppException {

    public CartEmptyException() {
        super(HttpStatus.BAD_REQUEST, "Cannot checkout an empty cart");
    }
}
