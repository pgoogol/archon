package com.pgoogol.kitchen.dictionary.application;

import com.pgoogol.kitchen.dictionary.domain.Dictionaries;
import org.springframework.stereotype.Service;

/**
 * Odczyt słowników dla formularza przepisu i filtrów wyszukiwarki.
 *
 * <p>Tabele słownikowe wchodzą razem ze schematem przepisu — do tego czasu
 * serwis oddaje puste listy, a nie błąd: front ma wtedy działający formularz
 * z pustymi podpowiedziami zamiast ekranu, który się nie ładuje.</p>
 */
@Service
public class DictionaryService {

    public Dictionaries all() {

        return Dictionaries.empty();
    }
}
