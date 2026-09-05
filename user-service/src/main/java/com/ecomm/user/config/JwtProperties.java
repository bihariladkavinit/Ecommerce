package com.ecomm.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code jwt.*} block from application.yml into a typed bean.
 *
 * <pre>
 * jwt:
 *   secret: ...
 *   access-token-expiry-ms: 900000
 *   refresh-token-expiry-ms: 604800000
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /** HS256 signing secret — must be at least 32 characters. */
    private String secret;

    /** Access token TTL in milliseconds (default 15 min). */
    private long accessTokenExpiryMs = 900_000L;

    /** Refresh token TTL in milliseconds (default 7 days). */
    private long refreshTokenExpiryMs = 604_800_000L;
}
