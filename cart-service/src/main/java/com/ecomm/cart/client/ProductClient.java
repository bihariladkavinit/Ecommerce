package com.ecomm.cart.client;

import com.ecomm.cart.dto.client.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign client for product-service.
 *
 * <p>Used by the cart service to:
 * <ul>
 *   <li>Verify a product exists and is active before adding it to the cart.</li>
 *   <li>Enrich cart items with the current name and price when returning
 *       {@code GET /cart}.</li>
 *   <li>Snapshot the unit price at checkout time before calling order-service.</li>
 * </ul>
 *
 * <p>The circuit breaker ({@code product-service} instance) and retry policy
 * are configured in {@code application.yml} under {@code resilience4j.*}.
 * The fallback fires when the circuit is open or the call times out / errors.
 */
@FeignClient(
        name = "product-service",
        fallback = ProductClientFallback.class
)
public interface ProductClient {

    /**
     * Fetches a product by its ID.
     *
     * @param id the product UUID
     * @return the product details, or the fallback result if the service is unavailable
     */
    @GetMapping("/api/v1/products/{id}")
    ProductResponse getProduct(@PathVariable("id") UUID id);
}
