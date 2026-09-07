package com.ecomm.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for the admin restock endpoint ({@code PUT /inventory/{productId}}).
 *
 * <p>{@code quantity} is the new absolute available stock level — not a delta.
 * This makes restocks idempotent: re-sending the same request with the same
 * quantity is safe and produces the same result.
 */
@Data
public class StockUpdateRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be 0 or greater")
    private Integer quantity;
}
