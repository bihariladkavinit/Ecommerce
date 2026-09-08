package com.ecomm.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full order response — returned by all order read endpoints and by
 * {@code POST /orders} (201) and {@code POST /orders/{id}/cancel} (200).
 *
 * <p>Also serialised into {@code idempotency_keys.response_body} so repeat
 * calls with the same {@code Idempotency-Key} return the cached response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID                    id;
    private UUID                    userId;
    private String                  status;
    private String                  sagaState;
    private BigDecimal              totalAmount;
    private List<OrderItemResponse> items;
    private OffsetDateTime          createdAt;
}
