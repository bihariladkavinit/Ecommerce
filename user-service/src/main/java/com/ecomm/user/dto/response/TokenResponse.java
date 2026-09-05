package com.ecomm.user.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    /** Access token TTL in milliseconds. */
    private long expiresIn;
}
