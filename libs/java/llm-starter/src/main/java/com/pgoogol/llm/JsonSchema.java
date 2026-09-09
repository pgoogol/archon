package com.pgoogol.llm;

import java.util.Objects;

/**
 * Schemat, którym provider pilnuje kształtu odpowiedzi. Trzymany jako tekst,
 * bo pochodzi z zasobu obok promptu — schemat pisany w kodzie rozjeżdżałby się
 * z promptem, który go opisuje.
 *
 * @param name nazwa przekazywana providerowi (widoczna w jego komunikatach błędów)
 * @param json treść schematu JSON Schema
 */
public record JsonSchema(String name, String json) {

    public JsonSchema {

        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(json, "json");
    }
}
