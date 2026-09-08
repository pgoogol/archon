package com.pgoogol.kitchen.dictionary.domain;

import java.util.List;

/**
 * Komplet słowników w jednym kawałku — formularz przepisu potrzebuje ich
 * wszystkich naraz, więc pobiera je jednym wywołaniem zamiast sześcioma.
 */
public record Dictionaries(
    List<Unit> units,
    List<DictionaryEntry> cuisines,
    List<DictionaryEntry> categories,
    List<DictionaryEntry> diets,
    List<DictionaryEntry> tags,
    List<DictionaryEntry> equipment) {

    public static Dictionaries empty() {

        return new Dictionaries(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
