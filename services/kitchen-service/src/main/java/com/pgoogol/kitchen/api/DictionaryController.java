package com.pgoogol.kitchen.api;

import com.pgoogol.kitchen.dictionary.application.DictionaryService;
import com.pgoogol.kitchen.dictionary.domain.Dictionaries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Słowniki jednym wywołaniem — formularz przepisu potrzebuje ich wszystkich
 * naraz, a zmieniają się rzadko.
 */
@RestController
@RequestMapping("/kitchen/api/v1")
@RequiredArgsConstructor
@Tag(name = "dictionaries", description = "Słowniki: jednostki, kuchnie, kategorie, diety, tagi, sprzęt")
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @GetMapping("/dictionaries")
    @Operation(summary = "Komplet słowników do formularza i filtrów")
    public DictionariesResponse dictionaries() {

        Dictionaries dictionaries = dictionaryService.all();
        return DictionariesResponse.of(dictionaries);
    }
}
