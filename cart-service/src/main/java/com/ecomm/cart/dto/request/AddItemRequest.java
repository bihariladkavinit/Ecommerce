package com.ecomm.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request body for {@code POST /cart/items}.
 *
 * <p>Adds a product to the cart, or merges the quantity with an existing entry
 * if the product is already present.
 */
@Data
public class AddItemRequest {

    @NotNull(message = "productId is required")
    private UUID productId;

    @Min(value = 1, message = "qty must be at least 1")
    private int qty;
}
