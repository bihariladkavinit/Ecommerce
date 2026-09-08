package com.ecomm.cart.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO received from order-service after a successful order creation.
 *
 * <p>This is passed directly back to the client as the {@code POST /cart/checkout}
 * response body — it represents the newly created order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID          id;
    private String        status;
    private String        sagaState;
    private BigDecimal    totalAmount;
    private OffsetDateTime createdAt;
}
