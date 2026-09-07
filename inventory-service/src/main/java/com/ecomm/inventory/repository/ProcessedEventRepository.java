package com.ecomm.inventory.repository;

import com.ecomm.inventory.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link ProcessedEvent}.
 *
 * <p>The only operation needed is {@code existsById(eventId)} — inherited from
 * {@link org.springframework.data.jpa.repository.JpaRepository} — which checks
 * whether a Kafka message has already been processed (idempotency guard).
 *
 * <p>Inserts are done via {@code save(new ProcessedEvent(eventId, null))}
 * within the same transaction as the business operation.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
