package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Klasy Resume* idą w kolejności nazw; ta otwiera kontekst z wolnym komponentem. */
@FixtureCases
@SpringJUnitConfig(SlowLifecycleConfig.class)
public class ResumeAFirstCases {

    @Test
    void runs() {
    }
}
