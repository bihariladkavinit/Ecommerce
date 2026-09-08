package com.ecomm.cart.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line item sent to order-service when creating an order.
 *
 * <p>The {@code unitPrice} is the price snapshotted from product-service at
 * checkout time — it is denormalized into the order so price changes after
 * checkout do not affect the order total.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {

    private UUID       productId;
    private int        qty;
    private BigDecimal unitPrice;
}
