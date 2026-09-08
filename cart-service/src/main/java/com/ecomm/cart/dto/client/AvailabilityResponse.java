package com.ecomm.cart.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO received from inventory-service
 * {@code GET /api/v1/inventory/{productId}/availability?qty={qty}}.
 *
 * <p>The {@code available} flag is the single fact the cart-service needs:
 * {@code true} means the requested quantity can be fulfilled at checkout time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityResponse {

    private UUID    productId;
    private boolean available;
}
