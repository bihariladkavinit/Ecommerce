package com.ecomm.user.exception;

import org.springframework.http.HttpStatus;

/** 404 — the requested resource does not exist. */
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, resource + " not found: " + id);
    }
}
