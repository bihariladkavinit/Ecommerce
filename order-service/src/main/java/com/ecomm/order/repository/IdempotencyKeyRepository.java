package com.ecomm.order.repository;

import com.ecomm.order.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    // PK is the key string — findById(key) is the primary access pattern.
}
