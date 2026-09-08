package com.ecomm.order.client;

import com.ecomm.order.dto.client.AddressResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for user-service — resolves a user's shipping address at
 * order creation time so it can be snapshot-stored in {@code orders.shipping_address}.
 *
 * <p>The address is fetched once and stored as a JSONB snapshot, meaning
 * subsequent saga steps (shipment.create.command) have the full address
 * without another user-service round-trip.
 *
 * <p>Circuit breaker ({@code user-service} instance) and retry are configured
 * in {@code application.yml} under {@code resilience4j.*}. The fallback fires
 * when the service is unavailable — order creation still proceeds with a
 * minimal address placeholder.
 */
@FeignClient(
        name     = "user-service",
        fallback = UserClientFallback.class
)
public interface UserClient {

    /**
     * Fetches all addresses for a user. The caller filters by {@code addressId}.
     * user-service does not expose a single-address-by-id endpoint for
     * service-to-service calls, so we fetch the list and find the right one.
     */
    @GetMapping("/api/v1/users/{userId}/addresses")
    List<AddressResponse> getAddresses(@PathVariable("userId") UUID userId);
}
