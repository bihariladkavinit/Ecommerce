package com.ecomm.product.service;

import com.ecomm.product.dto.request.ProductRequest;
import com.ecomm.product.dto.response.ProductResponse;
import com.ecomm.product.entity.OutboxEvent;
import com.ecomm.product.entity.Product;
import com.ecomm.product.exception.ResourceNotFoundException;
import com.ecomm.product.mapper.ProductMapper;
import com.ecomm.product.repository.OutboxEventRepository;
import com.ecomm.product.repository.ProductRepository;
import com.ecomm.product.repository.spec.ProductSpecification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Core product CRUD service.
 *
 * <p><strong>Outbox pattern:</strong> every create/update/delete writes an
 * {@link OutboxEvent} row in the <em>same transaction</em> as the business
 * change. The {@link OutboxRelayService} polls those rows and publishes them
 * to Kafka asynchronously — no dual-write risk.
 *
 * <p><strong>Redis cache:</strong> individual products are cached under
 * {@code product:{id}} with a configurable TTL (default 10 min). The cache
 * is evicted on any mutation. Cache failures are swallowed so they never
 * break the API response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private static final String CACHE_PREFIX      = "product:";
    private static final String AGGREGATE_TYPE    = "Product";
    private static final String EVENT_CREATED     = "product.created";
    private static final String EVENT_UPDATED     = "product.updated";

    private final ProductRepository     productRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CategoryService       categoryService;
    private final ProductMapper         productMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper          objectMapper;

    @Value("${product.cache.ttl-minutes:10}")
    private long cacheTtlMinutes;

    // ── Read ──────────────────────────────────────────────────────────

    /**
     * Returns a page of active products, optionally filtered by category
     * and/or a case-insensitive search string matched against name and description.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAll(UUID categoryId, String search, Pageable pageable) {
        return productRepository
                .findAll(ProductSpecification.build(categoryId, search, true), pageable)
                .map(productMapper::toResponse);
    }

    /**
     * Returns a single active product by ID.
     * Served from Redis cache when available; falls back to DB on miss.
     *
     * @throws ResourceNotFoundException if the product does not exist or is inactive
     */
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        String cacheKey = CACHE_PREFIX + id;

        // Cache hit
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ProductResponse.class);
            } catch (JsonProcessingException ex) {
                log.warn("Cache deserialisation failed for key={}", cacheKey);
            }
        }

        // Cache miss
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        ProductResponse response = productMapper.toResponse(product);
        cacheProduct(cacheKey, response);
        return response;
    }

    // ── Create ────────────────────────────────────────────────────────

    /**
     * Creates a new product and queues a {@code product.created} outbox event.
     * Both the product row and the outbox row are written in one transaction.
     */
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (request.getCategoryId() != null) {
            categoryService.assertExists(request.getCategoryId());
        }

        Product product = productMapper.toEntity(request);
        product.setActive(true);
        product = productRepository.save(product);

        enqueueOutboxEvent(product, EVENT_CREATED, buildCreatedPayload(product));

        log.info("Created product id={} name={}", product.getId(), product.getName());
        return productMapper.toResponse(product);
    }

    // ── Update ────────────────────────────────────────────────────────

    /**
     * Updates an existing product and queues a {@code product.updated} outbox event.
     * Redis cache for this product is evicted.
     *
     * @throws ResourceNotFoundException if the product does not exist (any active state)
     */
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (request.getCategoryId() != null) {
            categoryService.assertExists(request.getCategoryId());
        }

        productMapper.updateEntity(request, product);
        product = productRepository.save(product);

        evictCache(id);
        enqueueOutboxEvent(product, EVENT_UPDATED, buildUpdatedPayload(product));

        log.info("Updated product id={}", id);
        return productMapper.toResponse(product);
    }

    // ── Delete (soft) ─────────────────────────────────────────────────

    /**
     * Soft-deletes a product by setting {@code active = false}.
     * Queues a {@code product.updated} event so downstream services
     * (e.g. Inventory, Cart cache) can react.
     *
     * @throws ResourceNotFoundException if the product does not exist
     */
    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        product.setActive(false);
        productRepository.save(product);

        evictCache(id);
        enqueueOutboxEvent(product, EVENT_UPDATED, buildUpdatedPayload(product));

        log.info("Soft-deleted product id={}", id);
    }

    // ── Outbox helpers ────────────────────────────────────────────────

    private void enqueueOutboxEvent(Product product, String eventType, String payload) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(product.getId())
                .eventType(eventType)
                .payload(payload)
                .published(false)
                .build();
        outboxEventRepository.save(event);
    }

    private String buildCreatedPayload(Product product) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "productId",   product.getId().toString(),
                    "name",        product.getName(),
                    "price",       product.getPrice(),
                    "categoryId",  product.getCategoryId() != null
                                       ? product.getCategoryId().toString() : null
            ));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise product.created payload", ex);
            return "{}";
        }
    }

    private String buildUpdatedPayload(Product product) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "productId",   product.getId().toString(),
                    "name",        product.getName(),
                    "price",       product.getPrice(),
                    "categoryId",  product.getCategoryId() != null
                                       ? product.getCategoryId().toString() : null,
                    "active",      product.isActive()
            ));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialise product.updated payload", ex);
            return "{}";
        }
    }

    // ── Cache helpers ─────────────────────────────────────────────────

    private void cacheProduct(String cacheKey, ProductResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, cacheTtlMinutes, TimeUnit.MINUTES);
        } catch (Exception ex) {
            log.warn("Failed to cache product key={}: {}", cacheKey, ex.getMessage());
        }
    }

    private void evictCache(UUID id) {
        redisTemplate.delete(CACHE_PREFIX + id);
    }
}
