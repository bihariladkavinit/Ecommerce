package com.ecomm.shipment.dto.response;

import com.ecomm.shipment.entity.ShipmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for all shipment queries and mutations.
 *
 * <p>Returned by:
 * <ul>
 *   <li>{@code GET /shipments/{orderId}}</li>
 *   <li>{@code GET /shipments/track/{trackingNumber}}</li>
 *   <li>{@code PUT /shipments/{orderId}/status}</li>
 * </ul>
 */
@Data
@Builder
public class ShipmentResponse {

    private UUID           id;
    private UUID           orderId;
    private ShipmentStatus status;
    private String         carrier;
    private String         trackingNumber;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
