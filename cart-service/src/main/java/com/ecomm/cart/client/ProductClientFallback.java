package com.ecomm.cart.client;

import com.ecomm.cart.dto.client.ProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback for {@link ProductClient}.
 *
 * <p>When the product-service circuit is open or the call times out, this
 * fallback returns {@code null}. The service layer ({@code CartService})
 * handles a null product response gracefully:
 * <ul>
 *   <li>On {@code GET /cart}: the item is included with a placeholder name
 *       and zero price so the cart is still readable.</li>
 *   <li>On {@code POST /cart/items}: a null means we can't verify the product
 *       is active, so the add is rejected with a {@code 503}-style message.</li>
 *   <li>On checkout: the last-known price from the Redis-enriched cart is used
 *       if available; otherwise checkout is blocked.</li>
 * </ul>
 */
@Component
@Slf4j
public class ProductClientFallback implements ProductClient {

    @Override
    public ProductResponse getProduct(UUID id) {
        log.warn("ProductClient fallback triggered for productId={}", id);
        return null;
    }
}
