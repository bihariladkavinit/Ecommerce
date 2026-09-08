package com.ecomm.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line item in a {@link CreateOrderRequest}.
 *
 * <p>{@link #unitPrice} is the price snapshotted from product-service at
 * checkout time by the Cart Service — it is carried here so the order total
 * can be computed without a further product-service call.
 */
@Data
public class OrderItemRequest {

    @NotNull(message = "productId is required")
    private UUID productId;

    @Min(value = 1, message = "qty must be at least 1")
    private int qty;

    @NotNull(message = "unitPrice is required")
    @Positive(message = "unitPrice must be positive")
    private BigDecimal unitPrice;

    /**
     * Optional: product name snapshot supplied by Cart Service.
     * If absent, a placeholder is stored in the order item.
     */
    private String productName;
}
