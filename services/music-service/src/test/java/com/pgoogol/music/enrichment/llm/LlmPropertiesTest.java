package com.pgoogol.music.enrichment.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void provider_whenBlank_fallsBackToOpenai(String configured) {

        // given
        // pusty LLM_PROVIDER w .env dociera do aplikacji jako pusty łańcuch,
        // a nie jako brak zmiennej — wartość domyślna z konfiguracji go nie łapie

        // when
        LlmProperties properties = new LlmProperties(configured, null, null, null,
            "v1", 5, 2, 2048, 0.2, 500, null);

        // then
        assertThat(properties.provider()).isEqualTo("openai");
    }

    @Test
    void provider_whenSet_keepsConfiguredValue() {

        // given, when
        LlmProperties properties = new LlmProperties("anthropic", null, null, null,
            "v1", 5, 2, 2048, 0.2, 500, null);

        // then
        assertThat(properties.provider()).isEqualTo("anthropic");
    }
}
