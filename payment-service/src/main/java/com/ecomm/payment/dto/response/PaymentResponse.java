package com.ecomm.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for GET /payments/{orderId}.
 * Shape matches the api-contracts.md PaymentResponse definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID           id;
    private UUID           orderId;
    private BigDecimal     amount;
    private String         status;
    private String         gatewayRef;
    private OffsetDateTime createdAt;
}
