package com.pgoogol.diagnostics.core.report;

import java.util.Map;
import java.util.Optional;

/**
 * Podpowiedzi regułowe: dla znanego kodu wniosku jedno zdanie o typowej poprawce.
 * To punkt wyjścia dla człowieka albo narzędzia, które dostaje eksport, a nie diagnoza:
 * biblioteka nie zna modelu danych aplikacji.
 */
public class FindingHints {

    private static final Map<String, String> HINTS = Map.ofEntries(
        Map.entry("N_PLUS_ONE", "Dociągnij powiązania jednym zapytaniem: JOIN FETCH, @EntityGraph albo @BatchSize."),
        Map.entry("MISSING_BATCH", """
            Włącz hibernate.jdbc.batch_size i order_inserts; identyfikator IDENTITY wyłącza batch, \
            użyj sekwencji."""),
        Map.entry("SLOW_OPERATION", "Sprawdź plan zapytania (EXPLAIN ANALYZE) i indeksy na kolumnach z WHERE i JOIN."),
        Map.entry("OPERATION_COUNT", "Połącz zapytania w jedno albo pobierz dane zbiorczo zamiast w pętli."),
        Map.entry("LONG_TRANSACTION", "Wynieś z transakcji wywołania zewnętrzne i pracę, która nie dotyka bazy."),
        Map.entry("TRANSACTION_OPERATION_COUNT", "Zastąp pętlę zapytań jednym zapytaniem albo batchem."),
        Map.entry("READ_ONLY_CANDIDATE", "Oznacz metodę @Transactional(readOnly = true)."),
        Map.entry("CONNECTION_WAIT", "Zwiększ pulę albo skróć trzymanie połączeń; sprawdź wnioski CONNECTION_HOLD."),
        Map.entry("CONNECTION_HOLD", "Nie trzymaj transakcji podczas wywołań HTTP ani długich obliczeń."),
        Map.entry("OPEN_IN_VIEW", "Ustaw spring.jpa.open-in-view=false i dociągaj dane w warstwie serwisu."),
        Map.entry("FLUSH_COUNT", "Usuń saveAndFlush z pętli; nie przeplataj zapytań ze zmianami encji."),
        Map.entry("SLOW_FLUSH", "Zmniejsz liczbę zarządzanych encji albo włącz batch zapisów."),
        Map.entry("LARGE_FLUSH", "Przetwarzaj dane porcjami i czyść kontekst utrwalania między porcjami."),
        Map.entry("DIRTY_CHECKING", "Dla odczytów użyj readOnly albo projekcji DTO zamiast encji."),
        Map.entry("ENTITY_LOAD", "Dodaj paginację albo projekcję; nie ładuj całej tabeli do pamięci."),
        Map.entry("SECOND_LEVEL_CACHE", "Sprawdź, czy encja nadaje się do cache: rzadko zmieniana, czytana po kluczu."),
        Map.entry("REPOSITORY_HOTSPOT", "Zastąp wywołanie w pętli jedną metodą zbiorczą, np. findAllById."));

    /** Podpowiedź dla kodu; pusta dla kodu spoza listy, np. z analizy dopisanej w aplikacji. */
    public Optional<String> hintFor(String code) {

        return Optional.ofNullable(HINTS.get(code));
    }
}
