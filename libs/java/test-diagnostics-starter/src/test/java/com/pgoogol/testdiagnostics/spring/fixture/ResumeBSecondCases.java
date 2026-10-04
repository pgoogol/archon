package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Klasy Resume* idą w kolejności nazw; ta przełącza na inny kontekst, więc pierwszy zostaje wstrzymany. */
@FixtureCases
@SpringJUnitConfig(OtherConfig.class)
public class ResumeBSecondCases {

    @Test
    void runs() {
    }
}
