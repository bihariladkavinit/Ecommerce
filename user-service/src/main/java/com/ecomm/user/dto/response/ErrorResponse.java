package com.ecomm.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String traceId;

    /**
     * Populated only for validation errors — field → error message map.
     * e.g. {"email": "must be a valid address", "password": "must be at least 8 characters"}
     */
    private Map<String, String> fieldErrors;
}
