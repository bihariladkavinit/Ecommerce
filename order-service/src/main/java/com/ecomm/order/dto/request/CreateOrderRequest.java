package com.ecomm.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /orders}.
 *
 * <p>This endpoint is called internally by the Cart Service via OpenFeign
 * after it has validated prices and inventory availability. The Cart Service
 * supplies the price snapshot for each item so the order total can be computed
 * without an extra product-service round-trip.
 *
 * <p>Matches the contract defined in api-contracts.md:
 * <pre>
 * {
 *   "userId":            "uuid",
 *   "items":             [ { "productId": "uuid", "qty": 2, "unitPrice": 49.99 } ],
 *   "shippingAddressId": "uuid"
 * }
 * </pre>
 */
@Data
public class CreateOrderRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotEmpty(message = "items must not be empty")
    @Valid
    private List<OrderItemRequest> items;

    @NotNull(message = "shippingAddressId is required")
    private UUID shippingAddressId;
}
