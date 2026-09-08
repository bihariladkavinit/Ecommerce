package com.ecomm.payment.repository;

import com.ecomm.payment.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * SKIP_LOCKED ensures concurrent relay instances (e.g. rolling deployment)
 * each grab a disjoint batch — preventing duplicate Kafka publishes.
 * lock.timeout = -2 is Hibernate's constant for SKIP LOCKED.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
