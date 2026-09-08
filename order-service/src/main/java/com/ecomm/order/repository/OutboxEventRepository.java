package com.ecomm.order.repository;

import com.ecomm.order.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link OutboxEvent}.
 *
 * <p>The poll query uses {@code PESSIMISTIC_WRITE + SKIP_LOCKED} so that if
 * multiple instances of this service run concurrently (e.g. rolling deployment),
 * each instance claims a disjoint batch of rows — preventing duplicate Kafka
 * publishes from concurrent relay threads.
 *
 * <p>{@code lock.timeout = -2} is the Hibernate constant for SKIP_LOCKED
 * (PostgreSQL: {@code FOR UPDATE SKIP LOCKED}).
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches up to 100 unpublished outbox events ordered by creation time.
     * SKIP_LOCKED ensures concurrent relay instances never double-process rows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
