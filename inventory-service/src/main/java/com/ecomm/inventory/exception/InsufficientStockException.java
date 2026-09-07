package com.ecomm.inventory.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a reserve request cannot be fulfilled because available stock
 * is less than the requested quantity.
 *
 * <p>This is a domain-level 409 — the request itself is valid, but the
 * current resource state prevents it. The Order saga's reserve handler catches
 * this and writes a FAILED reply instead of propagating it as an HTTP error.
 */
public class InsufficientStockException extends AppException {

    private final UUID productId;

    public InsufficientStockException(UUID productId, int requested, int available) {
        super(HttpStatus.CONFLICT,
                "Insufficient stock for product " + productId +
                ": requested=" + requested + ", available=" + available);
        this.productId = productId;
    }

    public UUID getProductId() {
        return productId;
    }
}
