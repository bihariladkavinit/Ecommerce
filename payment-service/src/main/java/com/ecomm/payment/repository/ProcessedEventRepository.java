package com.ecomm.payment.repository;

import com.ecomm.payment.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    // existsById(eventId) is the primary access pattern — inherited from JpaRepository.
}
