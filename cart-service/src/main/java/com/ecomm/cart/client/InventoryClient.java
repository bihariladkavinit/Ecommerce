package com.ecomm.cart.client;

import com.ecomm.cart.dto.client.AvailabilityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Feign client for inventory-service.
 *
 * <p>Used exclusively during checkout to verify that every cart item has
 * sufficient stock before the order is submitted to order-service.
 *
 * <p>The inventory-service endpoint ({@code /inventory/{id}/availability})
 * is permit-all — it does not require a JWT — so no auth header forwarding
 * is needed for this specific call. The {@link FeignHeaderInterceptor} still
 * runs but the inventory-service security config explicitly permits this path.
 *
 * <p>The fallback returns {@code available=false}, which causes checkout to
 * surface a clear "availability unknown" error rather than silently submitting
 * an order that may fail downstream.
 */
@FeignClient(
        name = "inventory-service",
        fallback = InventoryClientFallback.class
)
public interface InventoryClient {

    /**
     * Checks whether at least {@code qty} units of a product are available.
     *
     * @param productId the product to check
     * @param qty       the requested quantity
     * @return availability result with a boolean {@code available} flag
     */
    @GetMapping("/api/v1/inventory/{productId}/availability")
    AvailabilityResponse checkAvailability(
            @PathVariable("productId") UUID productId,
            @RequestParam("qty") int qty);
}
