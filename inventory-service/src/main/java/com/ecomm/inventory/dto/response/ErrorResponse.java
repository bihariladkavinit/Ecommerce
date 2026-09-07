package com.ecomm.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Standard error envelope returned by {@link com.ecomm.inventory.exception.GlobalExceptionHandler}
 * for all error responses.
 *
 * <p>Shape matches the platform-wide contract defined in api-contracts.md:
 * <pre>
 * {
 *   "timestamp": "2026-09-07T10:15:30Z",
 *   "status":    404,
 *   "error":     "NOT_FOUND",
 *   "message":   "StockItem not found: ...",
 *   "path":      "/api/v1/inventory/...",
 *   "traceId":   "...",
 *   "fieldErrors": { ... }   // only present for validation errors
 * }
 * </pre>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private OffsetDateTime timestamp;
    private int            status;
    private String         error;
    private String         message;
    private String         path;
    private String         traceId;

    /** Field-level validation errors — only populated for HTTP 400 responses. */
    private Map<String, String> fieldErrors;
}
