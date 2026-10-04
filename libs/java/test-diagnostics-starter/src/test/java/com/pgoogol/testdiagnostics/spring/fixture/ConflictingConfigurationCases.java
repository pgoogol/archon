package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

/** Sprzeczne aliasy w {@code @ContextConfiguration}: Spring nie zbuduje z tego konfiguracji. */
@FixtureCases
@ContextConfiguration(value = "first.xml", locations = "second.xml")
public class ConflictingConfigurationCases {

    @Test
    void runs() {
    }
}
