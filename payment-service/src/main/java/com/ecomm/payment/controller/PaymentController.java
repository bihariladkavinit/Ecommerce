package com.ecomm.payment.controller;

import com.ecomm.payment.dto.response.PaymentResponse;
import com.ecomm.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal admin endpoint for payment lookup.
 *
 * Payment-service is NOT routed through the API Gateway — all charge/refund
 * activity is driven exclusively by Kafka. This endpoint exists for debugging
 * and admin inspection only, and requires ROLE_ADMIN.
 *
 * Accessible at: http://localhost:8086/api/v1/payments/{orderId}
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Internal admin endpoint for payment lookup (not Gateway-routed)")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Get payment by order ID",
            description = "Returns payment details for a given order. " +
                          "Requires ROLE_ADMIN. " +
                          "All charge/refund operations are Kafka-driven — this endpoint is read-only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "No payment exists for this orderId"),
            @ApiResponse(responseCode = "403", description = "ROLE_ADMIN required")
    })
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "UUID of the order") @PathVariable UUID orderId) {

        log.debug("GET /payments/{}", orderId);
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }
}
