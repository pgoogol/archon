package com.pgoogol.kitchen.dictionary.domain;

/**
 * Pozycja słownika prostego: kuchnia, kategoria, dieta, tag albo sprzęt.
 * Wszystkie mają ten sam kształt, więc dzielą jeden typ zamiast czterech
 * identycznych.
 */
public record DictionaryEntry(Long id, String name) {

}
