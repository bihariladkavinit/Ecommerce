package com.ecomm.cart.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Full cart response returned by all cart read and mutation endpoints
 * (except {@code DELETE /cart} which returns 204, and {@code POST /cart/checkout}
 * which returns an {@link com.ecomm.cart.dto.client.OrderResponse}).
 *
 * <p>The {@code total} is the sum of all {@link CartItemResponse#getSubtotal()} values,
 * computed in the service layer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private UUID                  userId;
    private List<CartItemResponse> items;
    private BigDecimal            total;
}
