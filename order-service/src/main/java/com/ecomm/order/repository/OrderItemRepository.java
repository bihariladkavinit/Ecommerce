package com.ecomm.order.repository;

import com.ecomm.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    // Items are accessed via Order.items (cascade) — this repository exists
    // for direct queries when needed (e.g. batch operations).
}
