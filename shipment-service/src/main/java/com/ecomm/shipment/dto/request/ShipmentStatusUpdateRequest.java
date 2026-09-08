package com.ecomm.shipment.dto.request;

import com.ecomm.shipment.entity.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for the admin status-update endpoint
 * ({@code PUT /shipments/{orderId}/status}).
 *
 * <p>The caller supplies only the desired target status. Transition validity
 * is enforced by {@link ShipmentStatus#canTransitionTo(ShipmentStatus)} inside
 * the service — not at the DTO level, so the API returns a meaningful 409
 * rather than a 400 for invalid progressions.
 */
@Data
public class ShipmentStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private ShipmentStatus status;
}
