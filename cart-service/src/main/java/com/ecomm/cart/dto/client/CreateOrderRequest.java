package com.ecomm.cart.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request body sent to order-service {@code POST /api/v1/orders}.
 *
 * <p>Matches the {@code CreateOrderRequest} contract in api-contracts.md:
 * <pre>
 * {
 *   "userId":            "uuid",
 *   "items":             [ { "productId": "uuid", "qty": 2, "unitPrice": 49.99 } ],
 *   "shippingAddressId": "uuid"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    private UUID                  userId;
    private List<OrderItemRequest> items;
    private UUID                  shippingAddressId;
}
