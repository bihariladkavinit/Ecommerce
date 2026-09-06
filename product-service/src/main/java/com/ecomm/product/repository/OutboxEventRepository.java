package com.ecomm.product.repository;

import com.ecomm.product.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches up to 100 unpublished events ordered by creation time.
     *
     * <p>{@code PESSIMISTIC_WRITE} + {@code SKIP_LOCKED} ensures that if
     * multiple instances of this service are running, each instance claims
     * a disjoint set of rows — no duplicate Kafka publishes from concurrent pollers.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
