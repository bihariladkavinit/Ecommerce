package com.ecomm.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI spec served at {@code /v3/api-docs} and Swagger UI
 * at {@code /swagger-ui.html}.
 *
 * <p>Registers a Bearer token security scheme so that the Swagger UI "Authorize"
 * button pre-populates the {@code Authorization: Bearer <token>} header —
 * useful for testing admin endpoints (restock) manually.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenApi() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Inventory Service API")
                        .description("Stock management, saga reserve/confirm/release, " +
                                "product event auto-initialisation")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
