package com.ecomm.inventory.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Response DTO for stock availability checks.
 *
 * <p>Returned by {@code GET /inventory/{productId}/availability?qty=N}.
 * Used internally by the Cart Service (Feign) to validate item quantities
 * before checkout.
 *
 * <p>Returns {@code available: false} (not a 404) when the product has no
 * stock record — the cart needs a clean boolean, not an error response.
 */
@Data
@Builder
public class AvailabilityResponse {

    private UUID    productId;

    /** {@code true} if {@code availableQty >= requestedQty}. */
    private boolean available;
}
