package com.ecomm.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight status response for {@code GET /orders/{id}/status}.
 * Lets clients poll saga progress without fetching the full order payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusResponse {

    /** High-level order status: PENDING, CONFIRMED, or CANCELLED. */
    private String status;

    /** Fine-grained saga orchestration state. */
    private String sagaState;
}
