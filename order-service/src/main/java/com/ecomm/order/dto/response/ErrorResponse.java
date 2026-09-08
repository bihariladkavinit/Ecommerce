package com.ecomm.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Standard error envelope returned by {@link com.ecomm.order.exception.GlobalExceptionHandler}.
 *
 * <pre>
 * {
 *   "timestamp":   "2026-09-08T10:15:30Z",
 *   "status":      404,
 *   "error":       "NOT_FOUND",
 *   "message":     "Order not found: ...",
 *   "path":        "/api/v1/orders/...",
 *   "traceId":     "...",
 *   "fieldErrors": { ... }   // only present for 400 validation errors
 * }
 * </pre>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private OffsetDateTime      timestamp;
    private int                 status;
    private String              error;
    private String              message;
    private String              path;
    private String              traceId;
    private Map<String, String> fieldErrors;
}
