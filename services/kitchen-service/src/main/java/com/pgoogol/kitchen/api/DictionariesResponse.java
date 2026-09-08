package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.dictionary.domain.Dictionaries;
import com.pgoogol.kitchen.dictionary.domain.DictionaryEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Komplet słowników potrzebnych formularzowi przepisu i filtrom")
public record DictionariesResponse(
    List<UnitResponse> units,
    List<DictionaryEntryResponse> cuisines,
    List<DictionaryEntryResponse> categories,
    List<DictionaryEntryResponse> diets,
    List<DictionaryEntryResponse> tags,
    List<DictionaryEntryResponse> equipment) {

    static DictionariesResponse of(Dictionaries dictionaries) {

        List<UnitResponse> units = dictionaries.units().stream().map(UnitResponse::of).toList();
        return new DictionariesResponse(
            units,
            entries(dictionaries.cuisines()),
            entries(dictionaries.categories()),
            entries(dictionaries.diets()),
            entries(dictionaries.tags()),
            entries(dictionaries.equipment()));
    }

    private static List<DictionaryEntryResponse> entries(List<DictionaryEntry> source) {

        return source.stream().map(DictionaryEntryResponse::of).toList();
    }
}
