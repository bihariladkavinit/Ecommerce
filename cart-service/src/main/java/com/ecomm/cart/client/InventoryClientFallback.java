package com.ecomm.cart.client;

import com.ecomm.cart.dto.client.AvailabilityResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback for {@link InventoryClient}.
 *
 * <p>Returns {@code available=false} when the inventory-service circuit is open
 * or the call times out. This is the safe default: treating unknown availability
 * as unavailable prevents over-selling.
 *
 * <p>The cart service will surface a {@code 409 ProductUnavailableException}
 * to the client, which can retry later when inventory-service has recovered.
 */
@Component
@Slf4j
public class InventoryClientFallback implements InventoryClient {

    @Override
    public AvailabilityResponse checkAvailability(UUID productId, int qty) {
        log.warn("InventoryClient fallback triggered for productId={} qty={}", productId, qty);
        return AvailabilityResponse.builder()
                .productId(productId)
                .available(false)
                .build();
    }
}
