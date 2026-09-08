package com.ecomm.cart.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed repository for shopping cart data.
 *
 * <p>Storage model:
 * <pre>
 *   Redis type : Hash
 *   Key        : "cart:{userId}"         e.g. "cart:a1b2-..."
 *   Hash field : "{productId}"           UUID as string
 *   Hash value : "{qty}"                 integer stored as string
 *   TTL        : {@code cart.ttl-days} (default 7 days),
 *                reset on every write so the cart expiry slides
 *                from the last interaction.
 * </pre>
 *
 * <p>All methods operate against the same Redis hash for a given user.
 * No Postgres interaction — this service owns no relational tables.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class CartRedisRepository {

    private static final String KEY_PREFIX = "cart:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${cart.ttl-days:7}")
    private long ttlDays;

    // ── Read ──────────────────────────────────────────────────────────

    /**
     * Returns all items in the user's cart as a map of
     * {@code productId (String) → qty (String)}.
     *
     * @param userId the authenticated user's UUID
     * @return a (possibly empty) map; never null
     */
    public Map<Object, Object> findAllItems(UUID userId) {
        Map<Object, Object> entries = hashOps().entries(key(userId));
        return entries != null ? entries : Collections.emptyMap();
    }

    /**
     * Returns the stored quantity for a specific product, if present.
     *
     * @param userId    the authenticated user's UUID
     * @param productId the product to look up
     * @return the quantity, or empty if the item is not in the cart
     */
    public Optional<Integer> getItem(UUID userId, UUID productId) {
        String value = (String) hashOps().get(key(userId), productId.toString());
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            log.warn("Corrupt qty value for userId={} productId={}: '{}'",
                    userId, productId, value);
            return Optional.empty();
        }
    }

    // ── Write ─────────────────────────────────────────────────────────

    /**
     * Sets (or overwrites) the quantity for a product in the user's cart,
     * then resets the TTL so the cart expiry slides from the last interaction.
     *
     * @param userId    the authenticated user's UUID
     * @param productId the product to add or update
     * @param qty       the new quantity (must be &gt; 0; callers are responsible for validation)
     */
    public void setItem(UUID userId, UUID productId, int qty) {
        String cartKey = key(userId);
        hashOps().put(cartKey, productId.toString(), String.valueOf(qty));
        redisTemplate.expire(cartKey, Duration.ofDays(ttlDays));
        log.debug("Cart item set userId={} productId={} qty={}", userId, productId, qty);
    }

    /**
     * Removes a single product entry from the user's cart hash.
     *
     * <p>If the product is not in the cart this is a no-op.
     *
     * @param userId    the authenticated user's UUID
     * @param productId the product to remove
     */
    public void removeItem(UUID userId, UUID productId) {
        hashOps().delete(key(userId), productId.toString());
        log.debug("Cart item removed userId={} productId={}", userId, productId);
    }

    /**
     * Deletes the entire cart hash for the user.
     *
     * <p>Called after a successful checkout to clear the cart, and also
     * exposed as the {@code DELETE /cart} endpoint.
     *
     * @param userId the authenticated user's UUID
     */
    public void clearCart(UUID userId) {
        redisTemplate.delete(key(userId));
        log.debug("Cart cleared userId={}", userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String key(UUID userId) {
        return KEY_PREFIX + userId.toString();
    }

    private HashOperations<String, Object, Object> hashOps() {
        return redisTemplate.opsForHash();
    }
}
