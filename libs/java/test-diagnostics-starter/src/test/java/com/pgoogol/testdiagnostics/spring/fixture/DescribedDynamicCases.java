package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Wzorzec dla opisu konfiguracji: metoda {@code @DynamicPropertySource}. Kontekst się tu nie ładuje. */
@FixtureCases
@SpringJUnitConfig(OtherConfig.class)
public class DescribedDynamicCases {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {

        registry.add("app.url", () -> "http://localhost");
    }

    @Test
    void runs() {
    }
}
