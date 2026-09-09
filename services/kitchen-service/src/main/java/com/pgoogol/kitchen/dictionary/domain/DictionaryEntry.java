package com.pgoogol.kitchen.dictionary.domain;

/**
 * Pozycja słownika prostego w postaci do odczytu. Wszystkie pięć słowników ma
 * ten sam kształt, więc dzielą jeden typ zamiast pięciu identycznych.
 */
public record DictionaryEntry(Long id, String name) {

    public static DictionaryEntry of(DictionaryTerm term) {

        return new DictionaryEntry(term.getId(), term.getName());
    }
}
