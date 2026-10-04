package com.pgoogol.testdiagnostics.core;

import java.util.List;
import java.util.Objects;

/**
 * Różnica jednego atrybutu konfiguracji kontekstu względem wcześniejszego środowiska.
 *
 * @param attribute identyfikator atrybutu, np. {@code profiles}, {@code beanOverrides};
 *                  raport tłumaczy go kluczem {@code attribute.<id>}
 * @param added     wartości, których wcześniej nie było
 * @param removed   wartości, których teraz nie ma
 */
public record AttributeDifference(String attribute, List<String> added, List<String> removed) {

    public AttributeDifference {

        Objects.requireNonNull(attribute, "atrybut jest wymagany");
        added = List.copyOf(added);
        removed = List.copyOf(removed);
    }
}
