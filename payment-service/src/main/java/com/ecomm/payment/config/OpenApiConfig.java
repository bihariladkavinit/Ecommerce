package com.ecomm.payment.config;

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
 * OpenAPI / Swagger UI: http://localhost:8086/api/v1/swagger-ui.html
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title       = "Payment Service API",
                version     = "v1",
                description = "Internal payment processing — Kafka-driven charge/refund via mock PSP gateway.",
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
        description = "Trusted user identity header forwarded by the API Gateway. " +
                      "ROLE_ADMIN required for all payment endpoints."
)
public class OpenApiConfig {
}
