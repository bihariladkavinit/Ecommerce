package com.ecomm.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents the current stock state for a single product.
 *
 * <p>The primary key ({@code productId}) mirrors {@code Product.id} from the
 * product-service — there is no cross-database FK; the relationship is by
 * convention only.
 *
 * <p><strong>Optimistic locking:</strong> the {@code @Version} field ({@code version})
 * maps to the {@code version} column in {@code stock_items}. Any concurrent update
 * will throw {@link org.springframework.orm.ObjectOptimisticLockingFailureException},
 * which the {@link com.ecomm.inventory.service.OptimisticLockRetryHelper} catches
 * and retries with exponential backoff.
 *
 * <p>Invariants enforced at the DB level (CHECK constraints in db-init.sql):
 * <ul>
 *   <li>{@code available_qty >= 0}</li>
 *   <li>{@code reserved_qty  >= 0}</li>
 * </ul>
 */
@Entity
@Table(name = "stock_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "productId")
public class StockItem {

    /**
     * Mirrors {@code Product.id} from product-service.
     * Set explicitly — no {@code @GeneratedValue} here.
     */
    @Id
    @Column(name = "product_id", updatable = false, nullable = false)
    private UUID productId;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    /**
     * JPA optimistic lock counter — maps to {@code version BIGINT} column.
     * Incremented automatically on every UPDATE.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
