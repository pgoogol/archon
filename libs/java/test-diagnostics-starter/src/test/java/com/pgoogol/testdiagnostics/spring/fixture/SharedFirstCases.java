package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@FixtureCases
@SpringJUnitConfig(SharedConfig.class)
@ActiveProfiles("fixture")
public class SharedFirstCases {

    @Test
    void usesSharedEnvironment() {
    }
}
