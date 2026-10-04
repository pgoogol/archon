package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Ta sama konfiguracja co {@link OverrideAPlainCases} plus mock, więc Spring uruchamia nowe środowisko. */
@FixtureCases
@SpringJUnitConfig(OverrideConfig.class)
public class OverrideBMockCases {

    @MockitoBean
    Greeter greeter;

    @Test
    void runs() {
    }
}
