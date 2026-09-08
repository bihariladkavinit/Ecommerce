package com.ecomm.cart.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Request body for {@code PUT /cart/items/{productId}}.
 *
 * <p>Sets the quantity of an existing cart item to the supplied value.
 * A qty of 0 is treated as a remove — the service layer delegates to
 * {@code removeItem} in that case, so callers can use either endpoint.
 */
@Data
public class UpdateItemRequest {

    @Min(value = 0, message = "qty must be 0 or greater (0 removes the item)")
    private int qty;
}
