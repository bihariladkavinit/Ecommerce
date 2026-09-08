package com.ecomm.cart.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO received from product-service {@code GET /api/v1/products/{id}}.
 *
 * <p>Only the fields the cart-service needs are mapped here — name, price, and
 * active flag. The full product-service response may contain more fields which
 * Jackson will silently ignore (via {@code DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES=false},
 * which is the Spring Boot default).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private UUID       id;
    private String     name;
    private BigDecimal price;
    private boolean    active;
}
