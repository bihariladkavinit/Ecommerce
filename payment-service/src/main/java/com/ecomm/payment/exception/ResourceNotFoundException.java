package com.ecomm.payment.exception;

import org.springframework.http.HttpStatus;
import java.util.UUID;

/** HTTP 404 — resource not found. */
public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resource, UUID id) {
        super(HttpStatus.NOT_FOUND, resource + " not found: " + id);
    }
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
