package com.ecomm.notification.repository;

import com.ecomm.notification.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link ProcessedEvent}.
 *
 * <p>{@code existsById(eventId)} — inherited from JpaRepository — is the only
 * operation needed: it checks whether a Kafka message has already been
 * processed (idempotency guard). Inserts are done via {@code save} within
 * the same transaction as the {@link com.ecomm.notification.entity.NotificationLog}
 * write to ensure atomicity.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
