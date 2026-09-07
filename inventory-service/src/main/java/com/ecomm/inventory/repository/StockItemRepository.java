package com.ecomm.inventory.repository;

import com.ecomm.inventory.entity.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link StockItem}.
 *
 * <p>The PK is {@code productId} (UUID), so standard {@code findById} /
 * {@code save} / {@code existsById} cover all use cases. Custom queries
 * are not needed — the service handles all business logic.
 */
@Repository
public interface StockItemRepository extends JpaRepository<StockItem, UUID> {
}
