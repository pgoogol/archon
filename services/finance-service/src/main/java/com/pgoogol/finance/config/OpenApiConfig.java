package com.pgoogol.finance.config;

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
    public OpenAPI financeOpenApi() {

        return new OpenAPI().info(new Info()
            .title("finance-service API")
            .description("""
                Finanse osobiste: konta i salda, kategorie, transakcje, waluty i kursy. \
                Kwoty jako liczby całkowite w jednostkach podrzędnych waluty.""")
            .version("v1"));
    }
}
