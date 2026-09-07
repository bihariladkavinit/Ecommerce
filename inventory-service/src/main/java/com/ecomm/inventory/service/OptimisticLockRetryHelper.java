package com.ecomm.inventory.service;

import com.ecomm.inventory.exception.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Reusable retry-with-exponential-backoff wrapper for optimistic locking conflicts.
 *
 * <p>When two concurrent transactions try to update the same {@code stock_items}
 * row, Hibernate throws {@link ObjectOptimisticLockingFailureException} on the
 * loser. Rather than surfacing this as a 409 immediately, this helper retries
 * the entire operation (re-read + re-apply) up to {@code maxAttempts} times,
 * waiting {@code initialBackoffMs * 2^attempt} milliseconds between retries.
 *
 * <p>If all attempts are exhausted a {@link ConflictException} (HTTP 409) is
 * thrown. This is appropriate for both REST callers (who can retry the request)
 * and saga command consumers (whose error handler will redeliver the message).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * StockItem saved = retryHelper.executeWithRetry(() -> {
 *     StockItem item = stockItemRepository.findById(id).orElseThrow();
 *     item.setAvailableQty(item.getAvailableQty() - qty);
 *     return stockItemRepository.save(item);
 * });
 * }</pre>
 *
 * <h3>Configuration</h3>
 * <pre>
 * inventory:
 *   lock-retry:
 *     max-attempts: 3       # default
 *     initial-backoff-ms: 50 # default
 * </pre>
 */
@Component
@Slf4j
public class OptimisticLockRetryHelper {

    private final int  maxAttempts;
    private final long initialBackoffMs;

    public OptimisticLockRetryHelper(
            @Value("${inventory.lock-retry.max-attempts:3}")     int  maxAttempts,
            @Value("${inventory.lock-retry.initial-backoff-ms:50}") long initialBackoffMs) {
        this.maxAttempts      = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
    }

    /**
     * Executes {@code operation} and retries on
     * {@link ObjectOptimisticLockingFailureException} with exponential backoff.
     *
     * @param operation the DB read-modify-write block to execute
     * @param <T>       return type of the operation
     * @return the result of the first successful execution
     * @throws ConflictException if all retry attempts are exhausted
     */
    public <T> T executeWithRetry(Supplier<T> operation) {
        int attempt = 0;
        while (true) {
            try {
                return operation.get();
            } catch (ObjectOptimisticLockingFailureException ex) {
                attempt++;
                if (attempt >= maxAttempts) {
                    log.warn("Optimistic lock conflict unresolved after {} attempts", maxAttempts);
                    throw new ConflictException(
                            "Stock update conflict after " + maxAttempts +
                            " retries — please retry the request");
                }
                long backoff = initialBackoffMs * (1L << attempt);   // 50, 100, 200 …
                log.debug("Optimistic lock conflict on attempt {}/{}, retrying in {}ms",
                        attempt, maxAttempts, backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Retry interrupted");
                }
            }
        }
    }
}
