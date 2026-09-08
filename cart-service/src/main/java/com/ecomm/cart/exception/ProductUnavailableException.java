package com.ecomm.cart.exception;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Thrown during checkout when one or more cart items fail the inventory
 * availability check — maps to HTTP 409.
 *
 * <p>The {@code unavailableProductIds} list identifies exactly which products
 * are out of stock so the caller can surface a helpful error message.
 */
public class ProductUnavailableException extends AppException {

    private final List<UUID> unavailableProductIds;

    public ProductUnavailableException(List<UUID> unavailableProductIds) {
        super(HttpStatus.CONFLICT,
                "The following products are unavailable: " + unavailableProductIds);
        this.unavailableProductIds = unavailableProductIds;
    }

    public List<UUID> getUnavailableProductIds() {
        return unavailableProductIds;
    }
}
