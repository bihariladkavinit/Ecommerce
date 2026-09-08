package com.ecomm.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * REST-level idempotency record for {@code POST /orders}.
 *
 * <p>When an order is created, a row is inserted with:
 * <ul>
 *   <li>{@link #key} — the {@code Idempotency-Key} header value (PK)</li>
 *   <li>{@link #requestHash} — SHA-256 of the serialised request body;
 *       used to detect key reuse with a different payload</li>
 *   <li>{@link #responseBody} — the serialised {@code OrderResponse} JSON,
 *       returned as-is on repeat calls with the same key</li>
 * </ul>
 *
 * <p>On a repeat call with the same key:
 * <ul>
 *   <li>Same hash → return cached {@link #responseBody} immediately</li>
 *   <li>Different hash → reject with 409 Conflict</li>
 * </ul>
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "key")
public class IdempotencyKey {

    @Id
    @Column(length = 100, nullable = false)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 255)
    private String requestHash;

    /** Cached JSON response body — populated after the order is persisted. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
