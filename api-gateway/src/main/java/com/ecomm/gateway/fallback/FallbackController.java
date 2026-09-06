package com.ecomm.gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Circuit breaker fallback endpoint.
 *
 * <p>All routes are configured with {@code fallbackUri=forward:/fallback}.
 * When Resilience4j opens a circuit (too many failures or timeouts), Spring
 * Cloud Gateway forwards the request here instead of to the downstream service.
 *
 * <p>Responds with the project's standard error envelope and HTTP 503.
 */
@RestController
public class FallbackController {

    /**
     * Handles fallback for all HTTP methods so that POST /cart/checkout, etc.
     * all reach this handler when their circuit is open.
     */
    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();

        // Attempt to surface the original path from the gateway's request attributes
        Object originalPath = exchange.getAttribute(
                org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String displayPath = originalPath != null ? originalPath.toString() : path;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "SERVICE_UNAVAILABLE");
        body.put("message", "Service temporarily unavailable. Please try again later.");
        body.put("path", displayPath);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
