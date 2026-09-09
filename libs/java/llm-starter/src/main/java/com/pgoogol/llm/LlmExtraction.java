package com.pgoogol.llm;

import java.util.Objects;

/**
 * Wynik ekstrakcji: odwzorowany rekord, surowy JSON, z którego powstał, i pełna
 * odpowiedź providera. Surowy JSON zostaje, bo przy sporze „model źle wyciągnął"
 * kontra „my źle sparsowaliśmy" tylko on rozstrzyga.
 */
public record LlmExtraction<T>(T value, String rawJson, LlmResponse response) {

    public LlmExtraction {

        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(rawJson, "rawJson");
        Objects.requireNonNull(response, "response");
    }

    public LlmUsage usage() {

        return response.usage();
    }
}
