package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Klasy Override* idą w kolejności nazw; ta uruchamia środowisko bez mocka. */
@FixtureCases
@SpringJUnitConfig(OverrideConfig.class)
public class OverrideAPlainCases {

    @Test
    void runs() {
    }
}
