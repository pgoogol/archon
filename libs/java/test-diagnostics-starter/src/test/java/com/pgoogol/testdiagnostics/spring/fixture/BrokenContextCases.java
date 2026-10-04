package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@FixtureCases
@SpringJUnitConfig(BrokenConfig.class)
public class BrokenContextCases {

    @Test
    void neverRuns() {
    }
}
