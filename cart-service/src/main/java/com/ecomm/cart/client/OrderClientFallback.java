package com.ecomm.cart.client;

import com.ecomm.cart.dto.client.CreateOrderRequest;
import com.ecomm.cart.dto.client.OrderResponse;
import com.ecomm.cart.exception.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for {@link OrderClient}.
 *
 * <p>Unlike the product and inventory fallbacks, there is no safe degraded
 * response here — if the order cannot be created, checkout must fail. This
 * fallback throws a {@link ConflictException} so the caller receives a clear
 * {@code 409} rather than a silent null or a generic {@code 500}.
 *
 * <p>The cart is NOT cleared when this fallback fires, so the user can retry
 * checkout once order-service recovers.
 */
@Component
@Slf4j
public class OrderClientFallback implements OrderClient {

    @Override
    public OrderResponse createOrder(String idempotencyKey, CreateOrderRequest request) {
        log.error("OrderClient fallback triggered — order-service unavailable. " +
                  "idempotencyKey={} userId={}", idempotencyKey, request.getUserId());
        throw new ConflictException(
                "Order service is currently unavailable — please retry your checkout");
    }
}
