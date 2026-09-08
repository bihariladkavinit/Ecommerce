package com.ecomm.order.repository;

import com.ecomm.order.entity.Order;
import com.ecomm.order.entity.OrderStatus;
import com.ecomm.order.entity.SagaState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Find an order by ID, verifying it belongs to the given user. */
    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    /** Paginated list of all orders for a user, newest first. */
    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find orders stuck in a given saga state whose {@code createdAt} is older
     * than {@code cutoff}. Used by the timeout reaper.
     */
    @Query("SELECT o FROM Order o WHERE o.sagaState = :sagaState AND o.createdAt < :cutoff")
    List<Order> findStuckOrders(
            @Param("sagaState") SagaState sagaState,
            @Param("cutoff") OffsetDateTime cutoff);

    /**
     * Find orders in a given high-level status and saga state older than cutoff.
     * Used by the reaper for multi-state queries.
     */
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.sagaState = :sagaState AND o.createdAt < :cutoff")
    List<Order> findStuckOrdersByStatusAndSagaState(
            @Param("status") OrderStatus status,
            @Param("sagaState") SagaState sagaState,
            @Param("cutoff") OffsetDateTime cutoff);
}
