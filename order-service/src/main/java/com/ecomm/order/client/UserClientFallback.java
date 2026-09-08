package com.ecomm.order.client;

import com.ecomm.order.dto.client.AddressResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fallback for {@link UserClient}.
 *
 * <p>Returns an empty list when the user-service circuit is open or the call
 * times out. The {@link com.ecomm.order.service.OrderService} handles an
 * empty address list gracefully — it stores a minimal JSONB snapshot
 * ({@code {"addressId":"...","resolved":false}}) and still creates the order.
 *
 * <p>The order will have an unresolved address; the shipment saga step will
 * use whatever is in the snapshot. This is a deliberate trade-off — a brief
 * user-service outage should not block order creation.
 */
@Component
@Slf4j
public class UserClientFallback implements UserClient {

    @Override
    public List<AddressResponse> getAddresses(UUID userId) {
        log.warn("UserClient fallback triggered for userId={} — address unresolvable", userId);
        return Collections.emptyList();
    }
}
