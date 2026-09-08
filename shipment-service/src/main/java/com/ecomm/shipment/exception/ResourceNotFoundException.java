package com.ecomm.shipment.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a requested shipment does not exist.
 * Maps to HTTP 404.
 */
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String resource, UUID id) {
        super(HttpStatus.NOT_FOUND, resource + " not found: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
