package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Ta sama konfiguracja co {@link DirtiesAFirstCases}, która zamknęła środowisko, więc start od nowa. */
@FixtureCases
@SpringJUnitConfig(DirtiesConfig.class)
public class DirtiesBSecondCases {

    @Test
    void runs() {
    }
}
