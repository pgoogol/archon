package com.pgoogol.testdiagnostics.spring.fixture;

import com.pgoogol.testdiagnostics.junit.fixture.FixtureCases;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Wzorzec dla opisu konfiguracji: profil i właściwości, w tym hasło. Kontekst się tu nie ładuje. */
@FixtureCases
@SpringJUnitConfig(OtherConfig.class)
@ActiveProfiles("alpha")
@TestPropertySource(properties = {"app.mode=fast", "app.password=s3cret"})
public class DescribedPropertiesCases {

    @Test
    void runs() {
    }
}
