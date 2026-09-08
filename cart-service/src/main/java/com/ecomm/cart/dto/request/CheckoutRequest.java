package com.ecomm.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request body for {@code POST /cart/checkout}.
 *
 * <p>The {@code shippingAddressId} must reference an address that belongs to
 * the authenticated user (validated by order-service against user-service data).
 * The {@code Idempotency-Key} header is handled separately at the controller
 * layer and passed through to order-service.
 */
@Data
public class CheckoutRequest {

    @NotNull(message = "shippingAddressId is required")
    private UUID shippingAddressId;
}
