package com.ecomm.inventory.service;

import com.ecomm.inventory.dto.request.StockUpdateRequest;
import com.ecomm.inventory.dto.response.AvailabilityResponse;
import com.ecomm.inventory.dto.response.StockResponse;
import com.ecomm.inventory.entity.StockItem;
import com.ecomm.inventory.exception.ResourceNotFoundException;
import com.ecomm.inventory.mapper.StockItemMapper;
import com.ecomm.inventory.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Core inventory service — handles the three REST-facing operations:
 * <ol>
 *   <li>Get current stock levels for a product.</li>
 *   <li>Check whether a requested quantity is available (used by Cart via Feign).</li>
 *   <li>Restock a product (admin operation).</li>
 * </ol>
 *
 * <p>Saga operations (reserve / confirm / release) live in
 * {@link SagaInventoryService}, which this service does not depend on —
 * keeping REST concerns and Kafka concerns cleanly separated.
 *
 * <p><strong>Restock semantics:</strong> {@code quantity} in
 * {@link StockUpdateRequest} is an <em>additive delta</em> — the supplied
 * amount is added to the current {@code available_qty}. This mirrors a real
 * warehouse "goods received" event: you receive N more units, not set the
 * shelf to exactly N.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final StockItemRepository stockItemRepository;
    private final StockItemMapper     stockItemMapper;
    private final OptimisticLockRetryHelper retryHelper;

    // ── Read ──────────────────────────────────────────────────────────

    /**
     * Returns current stock levels for a product.
     *
     * @throws ResourceNotFoundException if no stock record exists for the product
     */
    @Transactional(readOnly = true)
    public StockResponse getStock(UUID productId) {
        StockItem item = stockItemRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("StockItem", productId));
        return stockItemMapper.toResponse(item);
    }

    /**
     * Checks whether at least {@code requestedQty} units are available.
     *
     * <p>Returns {@code available: false} (not a 404) if no stock record exists —
     * the Cart Service needs a clean boolean, not an error response.
     *
     * @param productId    product to check
     * @param requestedQty number of units the caller wants
     * @return availability result, never null
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(UUID productId, int requestedQty) {
        boolean available = stockItemRepository.findById(productId)
                .map(item -> item.getAvailableQty() >= requestedQty)
                .orElse(false);

        log.debug("Availability check productId={} requested={} available={}", productId, requestedQty, available);
        return AvailabilityResponse.builder()
                .productId(productId)
                .available(available)
                .build();
    }

    // ── Write ─────────────────────────────────────────────────────────

    /**
     * Adds {@code request.quantity} units to the product's available stock.
     *
     * <p>If no stock record exists yet (e.g. before the product event consumer
     * has fired), one is created with {@code reservedQty=0}.
     *
     * <p>Each retry attempt runs in its own {@code REQUIRES_NEW} transaction via
     * {@link #doRestock} so that Hibernate flushes and the optimistic lock check
     * happens within that attempt's boundary — not deferred to the caller's transaction.
     *
     * @param productId product to restock
     * @param request   contains the quantity delta to add
     * @return updated stock levels
     */
    public StockResponse restock(UUID productId, StockUpdateRequest request) {
        StockItem item = retryHelper.executeWithRetry(() -> doRestock(productId, request));
        log.info("Restocked productId={} added={} newAvailableQty={}",
                productId, request.getQuantity(), item.getAvailableQty());
        return stockItemMapper.toResponse(item);
    }

    /**
     * Single restock attempt in its own transaction.
     * {@code REQUIRES_NEW} ensures Hibernate flushes and commits (or throws
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException})
     * before returning to the retry loop.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockItem doRestock(UUID productId, StockUpdateRequest request) {
        StockItem existing = stockItemRepository.findById(productId)
                .orElseGet(() -> StockItem.builder()
                        .productId(productId)
                        .availableQty(0)
                        .reservedQty(0)
                        .version(0L)
                        .build());

        existing.setAvailableQty(existing.getAvailableQty() + request.getQuantity());
        return stockItemRepository.save(existing);
    }
}
