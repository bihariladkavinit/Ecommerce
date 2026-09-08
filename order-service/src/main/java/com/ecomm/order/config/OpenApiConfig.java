package com.ecomm.order.config;

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
 * OpenAPI / Swagger configuration for order-service.
 *
 * <p>Accessible at:
 * <ul>
 *   <li>Swagger UI   — {@code http://localhost:8085/api/v1/swagger-ui.html}</li>
 *   <li>OpenAPI JSON — {@code http://localhost:8085/api/v1/v3/api-docs}</li>
 * </ul>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "Order Service API",
                version     = "v1",
                description = "Order lifecycle management — saga orchestrator for inventory, payment, and shipment.",
                contact     = @Contact(name = "Ecomm Platform Team")
        ),
        servers  = @Server(url = "/api/v1", description = "Default (context path)"),
        security = @SecurityRequirement(name = "GatewayAuth")
)
@SecurityScheme(
        name      = "GatewayAuth",
        type      = SecuritySchemeType.APIKEY,
        in        = SecuritySchemeIn.HEADER,
        paramName = "X-User-Id",
        description = "Trusted user identity header forwarded by the API Gateway after JWT validation."
)
public class OpenApiConfig {
    // Configuration via annotations above — no bean definitions needed.
}
