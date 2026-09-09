package com.pgoogol.llm.usage;

/**
 * Punkt wejścia serwisu w rozliczenie zużycia. Starter nie zna encji serwisu,
 * więc nie dopisuje tokenów do niczego sam — publikuje zdarzenie, a serwis
 * decyduje, gdzie je zapisze.
 *
 * <p>Implementacja jest wołana w wątku wywołującym, tuż po odpowiedzi providera.
 * Wyjątek z niej nie przewraca wywołania — zostaje zalogowany.
 */
@FunctionalInterface
public interface LlmUsageListener {

    void onUsage(LlmUsageEvent event);
}
