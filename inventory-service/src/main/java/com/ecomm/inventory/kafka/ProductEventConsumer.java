package com.ecomm.inventory.kafka;

import com.ecomm.inventory.entity.ProcessedEvent;
import com.ecomm.inventory.entity.StockItem;
import com.ecomm.inventory.kafka.dto.KafkaEnvelope;
import com.ecomm.inventory.repository.ProcessedEventRepository;
import com.ecomm.inventory.repository.StockItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Listens to {@code product.created} and {@code product.updated} events from
 * the Product Service and keeps the {@code stock_items} table self-consistent
 * with the product catalog.
 *
 * <h3>product.created</h3>
 * If no {@link StockItem} exists for the given {@code productId}, one is created
 * with {@code availableQty=0} and {@code reservedQty=0}. If a row already exists
 * (e.g. an Admin restocked before the event arrived) it is left untouched.
 *
 * <h3>product.updated</h3>
 * If {@code active=false} the product has been soft-deleted by an admin. The
 * stock item is intentionally <em>not</em> deleted — in-flight saga reservations
 * may still reference it and must be allowed to complete (confirm or release).
 * A warning is logged so operators are aware.
 *
 * <h3>Idempotency</h3>
 * Same pattern as {@link InventoryCommandConsumer}: check {@code processed_event}
 * before acting, insert the record in the same transaction as the stock change.
 *
 * <h3>Payload shape (from event-catalog.md)</h3>
 * <pre>
 * product.created / product.updated envelope payload:
 * { "productId": "uuid", "name": "...", "price": 9.99, "categoryId": "uuid", "active": true }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private final StockItemRepository      stockItemRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper             objectMapper;

    /**
     * Handles both {@code product.created} and {@code product.updated} from
     * a single listener method — the logic branches on the {@code eventType}
     * field inside the envelope.
     */
    @KafkaListener(
            topics  = {"product.created", "product.updated"},
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onProductEvent(@Payload String message, Acknowledgment ack) {
        log.debug("Received product event: {}", message);

        KafkaEnvelope envelope = parseEnvelope(message, ack);
        if (envelope == null) return;

        if (isDuplicate(envelope.getEventId(), ack)) return;

        try {
            String   eventType = envelope.getEventType();
            JsonNode payload   = envelope.getPayload();

            UUID productId = UUID.fromString(payload.get("productId").asText());

            if ("product.created".equals(eventType)) {
                handleProductCreated(productId);
            } else if ("product.updated".equals(eventType)) {
                boolean active = payload.has("active") && payload.get("active").asBoolean(true);
                handleProductUpdated(productId, active);
            } else {
                log.warn("Unexpected eventType={} on product topic — skipping", eventType);
            }

            recordProcessed(envelope.getEventId());
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process product event eventId={}: {}",
                    envelope.getEventId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Handlers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Creates a zero-quantity stock row if one does not already exist.
     * Idempotent: safe to call multiple times for the same productId.
     */
    private void handleProductCreated(UUID productId) {
        if (stockItemRepository.existsById(productId)) {
            log.debug("StockItem already exists for productId={} — skipping auto-init", productId);
            return;
        }

        StockItem newItem = StockItem.builder()
                .productId(productId)
                .availableQty(0)
                .reservedQty(0)
                .version(0L)
                .build();

        stockItemRepository.save(newItem);
        log.info("Auto-initialised StockItem for productId={} with availableQty=0", productId);
    }

    /**
     * Handles a product update event.
     *
     * <p>If the product was deactivated ({@code active=false}), we log a warning
     * but intentionally preserve the stock row so in-flight saga steps can complete.
     *
     * <p>If the product is still active and no stock row exists (e.g. the
     * {@code product.created} event was missed), one is created defensively.
     */
    private void handleProductUpdated(UUID productId, boolean active) {
        if (!active) {
            log.warn("Product productId={} has been deactivated — StockItem preserved " +
                    "for in-flight reservations. No stock changes applied.", productId);
            // Intentionally do NOT delete the stock row.
            return;
        }

        // Defensive upsert: if somehow no stock row exists for an active product, create one.
        if (!stockItemRepository.existsById(productId)) {
            log.info("StockItem missing for active productId={} — creating via product.updated", productId);
            StockItem newItem = StockItem.builder()
                    .productId(productId)
                    .availableQty(0)
                    .reservedQty(0)
                    .version(0L)
                    .build();
            stockItemRepository.save(newItem);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers  (same pattern as InventoryCommandConsumer)
    // ─────────────────────────────────────────────────────────────────

    private KafkaEnvelope parseEnvelope(String message, Acknowledgment ack) {
        try {
            return objectMapper.readValue(message, KafkaEnvelope.class);
        } catch (Exception ex) {
            log.error("Malformed product event message — skipping: {}", ex.getMessage());
            ack.acknowledge();
            return null;
        }
    }

    private boolean isDuplicate(UUID eventId, Acknowledgment ack) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate product event detected, skipping eventId={}", eventId);
            ack.acknowledge();
            return true;
        }
        return false;
    }

    private void recordProcessed(UUID eventId) {
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .build());
    }
}
