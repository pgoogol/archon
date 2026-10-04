package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Klasy Dirties* idą w kolejności nazw; ta zamyka swoje środowisko po sobie. */
@FixtureCases
@SpringJUnitConfig(DirtiesConfig.class)
@DirtiesContext
public class DirtiesAFirstCases {

    @Test
    void runs() {
    }
}
