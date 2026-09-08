package com.ecomm.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** A single line item within an {@link OrderResponse}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private UUID       productId;
    private String     productName;
    private int        qty;
    private BigDecimal unitPrice;
}
