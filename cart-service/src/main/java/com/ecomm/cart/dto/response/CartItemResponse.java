package com.ecomm.cart.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single enriched line item within a {@link CartResponse}.
 *
 * <p>Name and price come from a live product-service Feign call (or the
 * fallback placeholder when the circuit is open). The {@code subtotal} is
 * computed as {@code price × qty} in the service layer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private UUID       productId;
    private String     name;
    private BigDecimal price;
    private int        qty;
    private BigDecimal subtotal;
}
