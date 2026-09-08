package com.ecomm.cart.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration for cart-service.
 *
 * <p>Accessible at:
 * <ul>
 *   <li>Swagger UI  — {@code http://localhost:8084/api/v1/swagger-ui.html}</li>
 *   <li>OpenAPI JSON — {@code http://localhost:8084/api/v1/v3/api-docs}</li>
 * </ul>
 *
 * <p>The security scheme documents the internal header-based auth model:
 * the API Gateway validates the JWT and forwards {@code X-User-Id} /
 * {@code X-User-Roles}. For direct Swagger UI testing, supply the
 * {@code X-User-Id} header manually.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "Cart Service API",
                version     = "v1",
                description = "Shopping cart management — Redis-backed active cart, " +
                              "product enrichment via Feign, checkout to order-service.",
                contact     = @Contact(name = "Ecomm Platform Team")
        ),
        servers = {
                @Server(url = "/api/v1", description = "Default (context path)")
        },
        security = @SecurityRequirement(name = "GatewayAuth")
)
@SecurityScheme(
        name        = "GatewayAuth",
        type        = SecuritySchemeType.APIKEY,
        in          = SecuritySchemeIn.HEADER,
        paramName   = "X-User-Id",
        description = "Trusted user identity header forwarded by the API Gateway after JWT validation. " +
                      "Supply a valid user UUID for direct testing."
)
public class OpenApiConfig {
    // All configuration is via annotations above — no bean definitions needed.
}
