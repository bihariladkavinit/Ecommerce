package com.ecomm.inventory.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Response DTO for stock level queries.
 *
 * <p>Returned by {@code GET /inventory/{productId}} and
 * {@code PUT /inventory/{productId}} (restock).
 */
@Data
@Builder
public class StockResponse {

    private UUID productId;

    /** Units available for new orders (not reserved). */
    private int availableQty;

    /** Units currently reserved by in-flight orders. */
    private int reservedQty;
}
