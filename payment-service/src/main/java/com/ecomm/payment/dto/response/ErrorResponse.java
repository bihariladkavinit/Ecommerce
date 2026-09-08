package com.ecomm.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/** Standard error envelope — matches the platform-wide contract. */
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
