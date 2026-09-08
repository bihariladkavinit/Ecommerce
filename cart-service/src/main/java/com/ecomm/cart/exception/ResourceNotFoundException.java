package com.ecomm.cart.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a requested resource does not exist — maps to HTTP 404.
 *
 * <p>Typically used when a product looked up via Feign is not found in
 * the product-service, or when a cart item to update/remove is not present.
 */
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String resourceName, UUID id) {
        super(HttpStatus.NOT_FOUND, resourceName + " not found: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
