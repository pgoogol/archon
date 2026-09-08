package com.pgoogol.kitchen.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui.html; definicja: /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kitchenOpenApi() {

        return new OpenAPI().info(new Info()
            .title("kitchen-service API")
            .description("""
                Kuchnia: przepisy rozbite na składniki i kroki, import z linku, tekstu \
                i zdjęć, katalog składników oraz historia zmian.""")
            .version("v1"));
    }
}
