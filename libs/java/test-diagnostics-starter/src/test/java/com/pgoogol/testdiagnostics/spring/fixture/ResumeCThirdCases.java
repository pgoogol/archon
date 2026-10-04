package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Klasy Resume* idą w kolejności nazw; ta wraca do pierwszego kontekstu, który Spring musi wznowić. */
@FixtureCases
@SpringJUnitConfig(SlowLifecycleConfig.class)
public class ResumeCThirdCases {

    @Test
    void runs() {
    }
}
