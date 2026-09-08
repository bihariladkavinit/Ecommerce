package com.ecomm.cart.client;

import com.ecomm.cart.dto.client.CreateOrderRequest;
import com.ecomm.cart.dto.client.OrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for order-service.
 *
 * <p>Called once per checkout to create the order. The {@code Idempotency-Key}
 * header is forwarded from the original {@code POST /cart/checkout} request so
 * that if the cart service retries (or the client retries), order-service can
 * detect and deduplicate the repeat call.
 *
 * <p>This endpoint is Internal — it is not exposed through the Gateway for
 * external callers. The {@link FeignHeaderInterceptor} forwards {@code X-User-Id}
 * and {@code X-User-Roles} so order-service can build its security context.
 */
@FeignClient(
        name = "order-service",
        fallback = OrderClientFallback.class
)
public interface OrderClient {

    /**
     * Creates a new order from the validated cart contents.
     *
     * @param idempotencyKey client-supplied deduplication key, forwarded from checkout request
     * @param request        the order payload with user, items, and shipping address
     * @return the created order response
     */
    @PostMapping("/api/v1/orders")
    OrderResponse createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateOrderRequest request);
}
