package com.ecomm.cart.exception;

import com.ecomm.cart.dto.response.ErrorResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central exception handler — translates domain and framework exceptions into
 * the platform-standard {@link ErrorResponse} JSON shape.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link AppException} and all subclasses (ResourceNotFound, BadRequest, Conflict,
 *       CartEmpty, ProductUnavailable) — uses the status pinned on the exception.</li>
 *   <li>{@link MethodArgumentNotValidException} — HTTP 400 with per-field error map.</li>
 *   <li>{@link FeignException} — wraps downstream service errors as HTTP 502.</li>
 *   <li>{@link AccessDeniedException} — HTTP 403.</li>
 *   <li>Generic {@link Exception} — HTTP 500, full stack trace logged.</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Application exceptions ─────────────────────────────────────────

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleApp(AppException ex, HttpServletRequest req) {
        log.warn("Application error [{}]: {}", ex.getStatus(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), req, null);
    }

    // ── Validation ────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (first, second) -> first));

        log.warn("Validation failed: {}", fieldErrors);

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.name())
                .message("Validation failed")
                .path(req.getRequestURI())
                .traceId(MDC.get("traceId"))
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // ── Feign downstream errors ────────────────────────────────────────

    /**
     * Wraps errors from downstream Feign calls (Product, Inventory, Order services)
     * as a 502 Bad Gateway so the caller gets a clean error rather than an opaque 500.
     *
     * <p>Note: Resilience4j fallbacks intercept most failures before they propagate
     * here; this handler is the last-resort safety net for cases that slip through.
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeign(FeignException ex, HttpServletRequest req) {
        log.error("Downstream Feign error [status={}] on [{}]: {}",
                ex.status(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY,
                "Downstream service error — please retry", req, null);
    }

    // ── Access denied ─────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied: {}", req.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "Access denied", req, null);
    }

    // ── Catch-all ─────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on [{}]: {}", req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req, null);
    }

    // ── Helper ────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message,
            HttpServletRequest req, Map<String, String> fieldErrors) {

        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.name())
                .message(message)
                .path(req.getRequestURI())
                .traceId(MDC.get("traceId"))
                .fieldErrors(fieldErrors)
                .build());
    }
}
