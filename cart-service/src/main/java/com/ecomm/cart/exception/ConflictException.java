package com.ecomm.cart.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request conflicts with the current resource state — maps to HTTP 409.
 *
 * <p>Used during checkout when:
 * <ul>
 *   <li>One or more cart items are unavailable in inventory.</li>
 *   <li>The order-service Feign fallback fires (service unavailable).</li>
 * </ul>
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
