package com.ecomm.payment.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result returned by MockPaymentGateway for both charge and refund calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayResponse {

    /** true = gateway accepted the request; false = declined or unavailable. */
    private boolean success;

    /** Reference string returned on success, e.g. "mock-txn-a1b2c3d4". Null on failure. */
    private String gatewayRef;

    /** Human-readable decline reason. Null on success. */
    private String failureReason;
}
