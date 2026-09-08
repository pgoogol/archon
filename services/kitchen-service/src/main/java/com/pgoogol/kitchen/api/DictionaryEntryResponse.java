package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.dictionary.domain.DictionaryEntry;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pozycja słownika: kuchnia, kategoria, dieta, tag albo sprzęt")
public record DictionaryEntryResponse(Long id, @Schema(example = "polska") String name) {

    static DictionaryEntryResponse of(DictionaryEntry entry) {

        return new DictionaryEntryResponse(entry.id(), entry.name());
    }
}
